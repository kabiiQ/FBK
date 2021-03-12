package moe.kabii

import com.github.philippheuer.credentialmanager.CredentialManagerBuilder
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.philippheuer.events4j.simple.SimpleEventHandler
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.auth.providers.TwitchIdentityProvider
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import discord4j.core.DiscordClient
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import moe.kabii.command.Command
import moe.kabii.command.CommandManager
import moe.kabii.command.commands.twitch.TwitchBridgeOptions
import moe.kabii.data.GQLQueries
import moe.kabii.data.Keys
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MongoDBConnection
import moe.kabii.data.relational.PostgresConnection
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.event.message.MessageHandler
import moe.kabii.discord.invite.InviteWatcher
import moe.kabii.discord.tasks.OfflineUpdateHandler
import moe.kabii.discord.tasks.RecoverQueue
import moe.kabii.discord.trackers.ServiceWatcherManager
import moe.kabii.discord.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.discord.translation.Translator
import moe.kabii.net.NettyFileServer
import moe.kabii.discord.util.Metadata
import moe.kabii.discord.util.Uptime
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.terminal.TerminalListener
import moe.kabii.twitch.TwitchMessageHandler
import org.reflections.Reflections
import reactor.core.publisher.Mono

@Suppress("UNUSED_VARIABLE")
fun main() {
    // init global objects
    val mongo = MongoDBConnection
    val postgres = PostgresConnection
    LOG.info("FBK version: ${Metadata.buildInfo}")

    val gqlQueries = GQLQueries
    val keys = Keys.config
    val audio = AudioManager

    val reflection = Reflections("moe.kabii")

    // establish twitch connection
    val credential = CredentialManagerBuilder.builder().build()
    credential.registerIdentityProvider(TwitchIdentityProvider(
        keys[Keys.Twitch.client],
        keys[Keys.Twitch.secret],
        keys[Keys.Twitch.callback]
    ))
    val oAuth = OAuth2Credential("twitch", keys[Keys.Twitch.oauth])
    val twitch = TwitchClientBuilder.builder()
        .withEnableChat(true)
        .withCredentialManager(credential)
        .withChatAccount(oAuth)
        .build()

    val manager = CommandManager()
    val discordHandler = MessageHandler(manager)
    val twitchHandler = TwitchMessageHandler(manager)

    // register all commands with the command manager
    reflection.getSubTypesOf(Command::class.java)
        .forEach(manager::registerClass)

    // register twitch-discord bridge commands which require access to the twitch client
    manager.registerInstance(TwitchBridgeOptions.SetLinkedChannel(twitch))
    manager.registerInstance(TwitchBridgeOptions.UnlinkChannel(twitch))

    // establish discord connection
    val discord = DiscordClient.create(keys[Keys.Discord.token])
    Uptime
    val gateway = checkNotNull(discord.login().block())

    // begin listening for terminal commands
    TerminalListener(manager, gateway).launch()

    // start file server
    if(keys[Keys.Netty.host]) {
        NettyFileServer.server.start()
    }

    // start lifetime task threads
    ServiceWatcherManager(gateway).launch()
    val translator = Translator

    // perform initial offline checks
    val offlineChecks = gateway.guilds
        .flatMap { guild ->
            LOG.info("Connected to guild ${guild.name}")
            mono {
                OfflineUpdateHandler.runChecks(guild)
                InviteWatcher.updateGuild(guild)
                RecoverQueue.recover(guild)
            }
        }

    // primary message listener uses specific instance and is manually set up
    val onDiscordMessage = gateway.on(MessageCreateEvent::class.java)
        .map { event -> discordHandler.handle(event) }

    // all other event handlers simply recieve the event
    val eventListeners = reflection.getSubTypesOf(EventListener::class.java)
        .map { clazz ->
            val instance = clazz.kotlin.objectInstance
            if(instance == null) {
                LOG.error("KClass provided with no static instance: $clazz")
                return@map Mono.empty<Unit>()
            }
            LOG.debug("Registering EventHandler: ${instance.eventType} :: $clazz")
            gateway.on(instance.eventType.java)
                .flatMap(instance::wrapAndHandle)
        }

    val allListeners = eventListeners + listOf(offlineChecks, onDiscordMessage)

    // subscribe to bot lifetime discord events
    Mono.`when`(allListeners)
        .onErrorContinue { t, _ ->
            LOG.error("Uncaught exception in event handler: ${t.message}")
            LOG.warn(t.stackTraceString)
        }
        .subscribe()

    // subscribe to twitch events
    val onTwitchMessage = twitch.eventManager
        .getEventHandler(SimpleEventHandler::class.java)
        .onEvent(ChannelMessageEvent::class.java, twitchHandler::handle)

    // join any linked channels on twitch IRC
    runBlocking {
        val twitchChannels = GuildConfigurations.guildConfigurations.values
            .mapNotNull { config -> config.options.linkedTwitchChannel?.twitchid }
        TwitchParser.getUsers(twitchChannels).values
            .mapNotNull { user -> user.orNull()?.username }
            .forEach(twitch.chat::joinChannel)
    }
}