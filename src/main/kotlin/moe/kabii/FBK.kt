package moe.kabii

import discord4j.core.DiscordClientBuilder
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.gateway.intent.IntentSet
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import moe.kabii.command.Command
import moe.kabii.command.CommandManager
import moe.kabii.command.CommandRegistrar
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.data.flat.GQLQueries
import moe.kabii.data.flat.Keys
import moe.kabii.data.flat.KnownStreamers
import moe.kabii.data.mongodb.MongoDBConnection
import moe.kabii.data.relational.PostgresConnection
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.event.guild.welcome.WelcomeImageGenerator
import moe.kabii.discord.event.interaction.ChatCommandHandler
import moe.kabii.discord.invite.InviteWatcher
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.tasks.OfflineUpdateHandler
import moe.kabii.discord.tasks.RecoverQueue
import moe.kabii.discord.util.Metadata
import moe.kabii.discord.util.Uptime
import moe.kabii.net.NettyFileServer
import moe.kabii.net.api.videos.YoutubeVideosService
import moe.kabii.net.oauth.discord.DiscordOAuthRedirectServer
import moe.kabii.terminal.TerminalListener
import moe.kabii.trackers.ServiceWatcherManager
import moe.kabii.trackers.twitter.watcher.TwitterFeedSubscriber
import moe.kabii.translation.Translator
import moe.kabii.util.extensions.stackTraceString
import org.reflections.Reflections
import reactor.core.publisher.Mono
import kotlin.concurrent.thread

@Suppress("UNUSED_VARIABLE")
fun main() {
    // init global objects
    val threadPools = DiscordTaskPool
    val mongo = MongoDBConnection
    val postgres = PostgresConnection
    LOG.info("FBK version: ${Metadata.buildInfo}")

    val gqlQueries = GQLQueries
    val keys = Keys.config
    val audio = AudioManager

    val reflection = Reflections("moe.kabii")

    val manager = CommandManager()
    // register all commands with the command manager (internal registration)
    reflection
        .getSubTypesOf(Command::class.java)
        .forEach(manager::registerClass)

    // establish discord connection
    val discord = DiscordClientBuilder.create(keys[Keys.Discord.token])
        .build().gateway()
        .setEnabledIntents(IntentSet.all())
    Uptime
    val gateway = checkNotNull(discord.login().block())

    // register global commands with discord (external registration)
    // get config modules -> get instances that can be mapped into discord command json
    val modules = reflection
        .getSubTypesOf(ConfigurationModule::class.java)
        .mapNotNull { clazz -> clazz.kotlin.objectInstance }
    val globalCommands = CommandRegistrar.getAllGlobalCommands(modules)

    val rest = gateway.rest()
    val appId = checkNotNull(rest.applicationId.block())
    rest.applicationService
        .bulkOverwriteGlobalApplicationCommand(appId, globalCommands)
        .then()
        .block()

    // non-priority, blocking initialization that can make outgoing api calls thus is potentially very slow
    thread(start = true, name = "Initalization") {
        runBlocking {
            DiscordOAuthRedirectServer.server.start()
            val welcomer = WelcomeImageGenerator
            TwitterFeedSubscriber.verifySubscriptions()
            val streamers = KnownStreamers
            YoutubeVideosService.server.start()
            val translator = Translator.detector.detectLanguageOf("initalizing translator")
        }
    }

    // begin listening for terminal commands
    TerminalListener(manager, gateway).launch()

    // start file server
    if(Metadata.host) {
        NettyFileServer.server.start()
    }

    // start lifetime task threads
    val services = ServiceWatcherManager(gateway)
    services.launch()

    val discordHandler = ChatCommandHandler(manager, services)

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
    val onDiscordMessage = gateway.on(ChatInputInteractionEvent::class.java)
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
            gateway.on(instance.eventType.java, instance::wrapAndHandle)
        }

    val allListeners = eventListeners + listOf(offlineChecks, onDiscordMessage)

    // subscribe to bot lifetime discord events
    Mono.`when`(allListeners)
        .onErrorResume { t ->
            LOG.error("Uncaught exception in event handler: ${t.message}")
            LOG.warn(t.stackTraceString)
            Mono.empty()
        }
        .subscribe()
}