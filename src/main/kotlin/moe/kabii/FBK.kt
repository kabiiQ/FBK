package moe.kabii

import discord4j.core.DiscordClient
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.reactor.mono
import moe.kabii.command.Command
import moe.kabii.command.CommandManager
import moe.kabii.data.GQLQueries
import moe.kabii.data.Keys
import moe.kabii.data.mongodb.MongoDBConnection
import moe.kabii.data.relational.PostgresConnection
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.event.guild.welcome.WelcomeImageGenerator
import moe.kabii.discord.event.message.MessageHandler
import moe.kabii.discord.invite.InviteWatcher
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.tasks.OfflineUpdateHandler
import moe.kabii.discord.tasks.RecoverQueue
import moe.kabii.discord.trackers.ServiceWatcherManager
import moe.kabii.discord.translation.Translator
import moe.kabii.discord.util.Metadata
import moe.kabii.discord.util.Uptime
import moe.kabii.net.NettyFileServer
import moe.kabii.terminal.TerminalListener
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
    // register all commands with the command manager
    reflection.getSubTypesOf(Command::class.java)
        .forEach(manager::registerClass)

    // establish discord connection
    val discord = DiscordClient.create(keys[Keys.Discord.token])
    Uptime
    val gateway = checkNotNull(discord.login().block())

    // non-priority, blocking initialization that can make outgoing api calls thus is potentially very slow
    thread(start = true, name = "Initalization") {
        val translator = Translator.detector.detectLanguageOf("initalizing translator")
        val welcomer = WelcomeImageGenerator
    }

    // begin listening for terminal commands
    TerminalListener(manager, gateway).launch()

    // start file server
    if(keys[Keys.Netty.host]) {
        NettyFileServer.server.start()
    }

    // start lifetime task threads
    val services = ServiceWatcherManager(gateway)
    services.launch()

    val discordHandler = MessageHandler(manager, services)

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
}