package moe.kabii

import com.github.philippheuer.credentialmanager.CredentialManagerBuilder
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.auth.providers.TwitchIdentityProvider
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.audit.ActionType
import discord4j.core.`object`.audit.ChangeKey
import discord4j.core.event.domain.PresenceUpdateEvent
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.event.domain.channel.TextChannelDeleteEvent
import discord4j.core.event.domain.guild.*
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.lifecycle.ReconnectEvent
import discord4j.core.event.domain.message.*
import discord4j.core.event.domain.role.RoleDeleteEvent
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.mono
import moe.kabii.data.Keys
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MongoDBConnection
import moe.kabii.data.relational.PostgresConnection
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.DiscordMessageHandler
import moe.kabii.discord.event.EventHandler
import moe.kabii.discord.event.guild.*
import moe.kabii.discord.event.user.*
import moe.kabii.discord.invite.InviteWatcher
import moe.kabii.discord.tasks.AutojoinVoice
import moe.kabii.discord.tasks.OfflineUpdateHandler
import moe.kabii.discord.tasks.RecoverQueue
import moe.kabii.discord.tasks.ReminderWatcher
import moe.kabii.discord.trackers.anime.MediaListWatcher
import moe.kabii.discord.trackers.streams.StreamWatcher
import moe.kabii.discord.trackers.streams.twitch.TwitchParser
import moe.kabii.joint.CommandManager
import moe.kabii.net.NettyFileServer
import moe.kabii.structure.Metadata
import moe.kabii.structure.Uptime
import moe.kabii.structure.orNull
import moe.kabii.structure.stackTraceString
import moe.kabii.twitch.TwitchMessageHandler
import org.reflections.Reflections
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

fun main() {
    // init
    val mongo = MongoDBConnection
    val postgres = PostgresConnection
    val version = Metadata.current
    val keys = Keys.config
    val audio = AudioManager

    val reflection = Reflections("moe.kabii")

    // twitch connection
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
    val discordHandler = DiscordMessageHandler(manager, twitch)
    val twitchHandler = TwitchMessageHandler(manager)

    reflection.getSubTypesOf(Command::class.java)
        .forEach(manager::register)

    manager.register(object : Command("test") {
        init {
            discord {
                embed("Hello World!").subscribe()
            }
        }
    })

    // discord connection
    val discord = DiscordClientBuilder(keys[Keys.Discord.token]).build()

    // task threads
    val listWatcher = MediaListWatcher(discord)
    val streamWatcher = StreamWatcher(discord)
    val reminderWatcher = ReminderWatcher(discord)

    // discord4j event listeners
    val events = discord.eventDispatcher

    val onGuildReady = events.on(ReadyEvent::class.java)
        .map { event -> event.guilds.size }
        .doOnNext { count ->
            LOG.info("Connecting to $count guilds.")
        }
        .flatMap { count ->
            val recoverQueue = RecoverQueue()
            events.on(GuildCreateEvent::class.java)
                .take(count.toLong())
                .map(GuildCreateEvent::getGuild)
                .doOnNext(OfflineUpdateHandler::runChecks)
                .doOnNext(AutojoinVoice::autoJoin)
                .doOnNext { guild ->
                    InviteWatcher.updateGuild(guild)
                    recoverQueue.recover(guild)
                    LOG.info("Connected to guild ${guild.name}")
                }
        }

    val onInitialReady = events.on(ReadyEvent::class.java)
        .take(1)
        .map { event -> event.guilds.size }
        .flatMap { count ->
            events.on(GuildCreateEvent::class.java)
                .take(count.toLong())
                .collectList()
        }
        .doOnNext { _ ->
            // init tasks
            Uptime
            listWatcher.start()
            streamWatcher.start()
            reminderWatcher.start()
        }

    // assemble "normal" event handlers
    val handlers = reflection.getSubTypesOf(EventHandler::class.java)
        .map { clazz ->
            val instance = clazz.kotlin.objectInstance
            if(instance == null) {
                LOG.error("KClass provided with no static instance: $clazz")
                return@map Mono.empty<Unit>()
            }
            LOG.info("Registering EventHandler: ${instance.eventType} :: $clazz")
            events.on(instance.eventType.java)
                .flatMap(instance::wrapAndHandle)
        }

    // special event handlers
    val onReactionAdd = events.on(ReactionAddEvent::class.java)
            .doOnNext(ReactionHandler::handleReactionAdded)
    val onReactionRemove = events.on(ReactionRemoveEvent::class.java)
        .doOnNext(ReactionHandler::handleReactionRemoved)
    val onJoin = events.on(MemberJoinEvent::class.java)
        .map(MemberJoinEvent::getMember)
        .doOnNext { member -> JoinHandler.handle(member) }
    val onPart = events.on(MemberLeaveEvent::class.java)
            .doOnNext { event ->
                PartHandler.handle(event.guildId, event.user, event.member.orNull())
            }
    val onGatewayReconnection = events.on(ReconnectEvent::class.java)
        .doOnNext { Uptime.update() }

    val onDiscordMessage = events.on(MessageCreateEvent::class.java)
        .flatMap(discordHandler::handle)

    // twitch4j event listeners
    val onTwitchMessage = twitch.eventManager.onEvent(ChannelMessageEvent::class.java)
            .filter { it.user.name.toLowerCase() != "ai_kizuna" }
            .doOnNext(twitchHandler::handle)

    val subscribers = mutableListOf(
        onJoin, onPart, onGatewayReconnection, onReactionAdd,
        onReactionRemove, onGuildReady, onInitialReady,
        onDiscordMessage, onTwitchMessage
    ).plus(handlers)

    // subscribe to bot lifetime events
    Mono.`when`(subscribers)
        .onErrorContinue { t, _ ->
            LOG.error("Uncaught exception in event handler: ${t.message}")
            LOG.warn(t.stackTraceString)
        }
        .subscribe()

    // start file server
    if(keys[Keys.Netty.host]) {
        NettyFileServer.server.start()
    }

    // join any linked channels on twitch IRC
    GuildConfigurations.guildConfigurations.values
        .mapNotNull { it.options.linkedTwitchChannel?.twitchid }
        .let(TwitchParser::getUsers).values
        .mapNotNull { user -> user.orNull()?.username }
        .forEach(twitch.chat::joinChannel)

    // login
    discord.login().subscribe()
}