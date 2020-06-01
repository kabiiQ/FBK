package moe.kabii

import com.github.philippheuer.credentialmanager.CredentialManagerBuilder
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.philippheuer.events4j.reactor.ReactorEventHandler
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.auth.providers.TwitchIdentityProvider
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.guild.GuildCreateEvent
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.event.domain.guild.MemberLeaveEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.lifecycle.ReconnectEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import discord4j.rest.entity.RestMember
import discord4j.rest.request.BucketGlobalRateLimiter
import moe.kabii.data.Keys
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MongoDBConnection
import moe.kabii.data.relational.PostgresConnection
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.DiscordMessageHandler
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.event.guild.ReactionHandler
import moe.kabii.discord.event.user.JoinHandler
import moe.kabii.discord.event.user.PartHandler
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
import reactor.core.publisher.Mono

fun main() {
    // init global objects
    val mongo = MongoDBConnection
    val postgres = PostgresConnection
    val version = Metadata.current
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

    // register all commands
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

    // establish discord connection
    val discord = DiscordClient.create(keys[Keys.Discord.token])
    val gateway = discord.login().block()!!

    // task threads
    val listWatcher = MediaListWatcher(gateway)
    val streamWatcher = StreamWatcher(gateway)
    val reminderWatcher = ReminderWatcher(gateway)

    // start file server
    if(keys[Keys.Netty.host]) {
        NettyFileServer.server.start()
    }

    // listen for initial connection event, set initial state
    val onInitialReady = gateway.on(ReadyEvent::class.java)
        .take(1)
        .map { event -> event.guilds.size }
        .flatMap { count ->
            gateway.on(GuildCreateEvent::class.java)
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

    // listen for guild connections, request any missing information
    val onGuildReady = gateway.on(ReadyEvent::class.java)
        .map { event -> event.guilds.size }
        .doOnNext { count ->
            LOG.info("Connecting to $count guilds.")
        }
        .flatMap { count ->
            gateway.on(GuildCreateEvent::class.java)
                .take(count.toLong())
                .map(GuildCreateEvent::getGuild)
                .doOnNext(OfflineUpdateHandler::runChecks)
                .doOnNext(AutojoinVoice::autoJoin)
                .doOnNext { guild ->
                    InviteWatcher.updateGuild(guild)
                    RecoverQueue.recover(guild)
                    LOG.info("Connected to guild ${guild.name}")
                }
        }

    // event handlers which simply recieve the event
    val handlers = reflection.getSubTypesOf(EventListener::class.java)
        .map { clazz ->
            val instance = clazz.kotlin.objectInstance
            if(instance == null) {
                LOG.error("KClass provided with no static instance: $clazz")
                return@map Mono.empty<Unit>()
            }
            LOG.info("Registering EventHandler: ${instance.eventType} :: $clazz")
            gateway.on(instance.eventType.java)
                .flatMap(instance::wrapAndHandle)
        }

    // event handlers requiring manual setup
    val onReactionAdd = gateway.on(ReactionAddEvent::class.java)
            .doOnNext(ReactionHandler::handleReactionAdded)
    val onReactionRemove = gateway.on(ReactionRemoveEvent::class.java)
        .doOnNext(ReactionHandler::handleReactionRemoved)
    val onJoin = gateway.on(MemberJoinEvent::class.java)
        .map(MemberJoinEvent::getMember)
        .doOnNext { member -> JoinHandler.handle(member) }
    val onPart = gateway.on(MemberLeaveEvent::class.java)
            .doOnNext { event ->
                PartHandler.handle(event.guildId, event.user, event.member.orNull())
            }

    val onGatewayReconnection = gateway.on(ReconnectEvent::class.java)
        .doOnNext { Uptime.update() }

    val onDiscordMessage = gateway.on(MessageCreateEvent::class.java)
        .flatMap(discordHandler::handle)

    // twitch4j event listener
    val onTwitchMessage = twitch.eventManager
        .getEventHandler(ReactorEventHandler::class.java)
        .onEvent(ChannelMessageEvent::class.java, twitchHandler::handle)

    val subscribers = mutableListOf(
        onJoin, onPart, onGatewayReconnection, onReactionAdd,
        onReactionRemove, onGuildReady, onInitialReady,
        onDiscordMessage
    ).plus(handlers)

    // subscribe to bot lifetime events
    Mono.`when`(subscribers)
        .onErrorContinue { t, _ ->
            LOG.error("Uncaught exception in event handler: ${t.message}")
            LOG.warn(t.stackTraceString)
        }
        .subscribe()

    // join any linked channels on twitch IRC
    GuildConfigurations.guildConfigurations.values
        .mapNotNull { it.options.linkedTwitchChannel?.twitchid }
        .let(TwitchParser::getUsers).values
        .mapNotNull { user -> user.orNull()?.username }
        .forEach(twitch.chat::joinChannel)
}