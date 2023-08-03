package moe.kabii.instances

import discord4j.common.util.Snowflake
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import discord4j.core.event.EventDispatcher
import discord4j.gateway.intent.Intent
import discord4j.gateway.intent.IntentSet
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.command.CommandManager
import moe.kabii.command.commands.configuration.setup.MusicConfig
import moe.kabii.command.commands.configuration.setup.StarboardConfig
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.documentation.CommandDocumentor
import moe.kabii.command.registration.GlobalCommandRegistrar
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.invite.InviteWatcher
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.tasks.OfflineUpdateHandler
import moe.kabii.discord.util.Uptime
import moe.kabii.trackers.ServiceWatcherManager
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.stackTraceString
import org.reflections.Reflections
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

data class InstanceProperties(
    val messageContentAccess: Boolean,
    val presenceAccess: Boolean,
    val musicFeaturesEnabled: Boolean,
    val starboardEnabled: Boolean
) {
    constructor(inst: InstanceDataLoader.Instance) : this(inst.messageContent, inst.presences, inst.musicEnabled, inst.starboardEnabled)
}

class FBK(
    val clientId: Int,
    val client: GatewayDiscordClient,
    val username: String,
    val discriminator: String,
    val properties: InstanceProperties
) {
    val uptime = Uptime()

    override fun equals(other: Any?): Boolean = (other as? FBK)?.clientId == clientId
    override fun hashCode() = clientId
}

class DiscordInstances {

    private lateinit var instances: Map<Int, FBK>
    lateinit var manager: CommandManager
    lateinit var services: ServiceWatcherManager

    fun all() = instances.values.toList()

    operator fun get(instanceId: Int) = instances.getValue(instanceId)

    operator fun get(client: GatewayDiscordClient) = instances.values.find { fbk ->
        fbk.client.selfId == client.selfId
    }.run(::checkNotNull)

    fun check(instanceId: Int) = instances[instanceId]

    suspend fun getByGuild(guildId: Snowflake) = instances.values.filter { fbk ->
        fbk.client.guilds
            .collectList()
            .awaitSingle()
            .find { guild -> guild.id == guildId } != null
    }

    fun getByDiscriminator(discriminator: String) = instances.values
        .find { fbk -> fbk.discriminator == discriminator }

    suspend fun launchInstances(): List<Flux<*>> {
        val reflection = Reflections("moe.kabii")

        manager = CommandManager()
        // register all commands with the command manager (internal registration)
        reflection
            .getSubTypesOf(Command::class.java)
            .forEach(manager::registerClass)

        // get global commands for registration with discord
        // get config modules -> get instances that can be mapped into discord commands
        val modules = reflection
            .getSubTypesOf(ConfigurationModule::class.java)
            .mapNotNull { clazz -> clazz.kotlin.objectInstance }
            .filterNot { mod -> mod == StarboardConfig.StarboardModule || mod == MusicConfig.MusicBot.MusicSettingsModule }

        val globalCommands = GlobalCommandRegistrar.getAllGlobalCommands(modules)

        // build dynamic config commands that are not used on all instances
        val starboardCommand = GlobalCommandRegistrar.buildConfigCommand(StarboardConfig.StarboardModule)
        val musicCfgCommand = GlobalCommandRegistrar.buildConfigCommand(MusicConfig.MusicBot.MusicSettingsModule)

        // build all feature specific commands that are not used on all instances
        val musicCommands = GlobalCommandRegistrar.getFeatureCommands("music") + musicCfgCommand

        val allCommands = globalCommands + musicCommands

        // generate command self-documentation
        CommandDocumentor.writeCommandDoc(manager, allCommands)
        LOG.info("Command List.md updated.")

        val eventListeners = reflection.getSubTypesOf(EventListener::class.java)
            .mapNotNull { clazz ->
                val obj = clazz.kotlin.objectInstance
                if(obj != null) {
                    LOG.debug("Registering EventHandler object :: $clazz")
                    obj
                } else {
                    // many event listeners require access to the discord instances and are not singletons.
                    val instance = clazz
                        .constructors[0]
                        .newInstance(this)
                        .run(clazz::cast)
                    LOG.debug("Found EventHandler class: $clazz :: ${instance.eventType}")
                    instance
                }
            }

        val publishers = mutableListOf<Flux<*>>()

        // load FBK instances from json and connect each
        instances = InstanceDataLoader.loadFromFile().associate { instance ->

            // connect instance to discord
            val discord = DiscordClientBuilder
                .create(instance.token)
                .build()
                .gateway()
                .run {
                    val intents = IntentSet.all()
                        .run { if(!instance.messageContent) andNot(IntentSet.of(Intent.MESSAGE_CONTENT)) else this }
                        .run { if(!instance.presences) andNot(IntentSet.of(Intent.GUILD_PRESENCES)) else this }
                    setEnabledIntents(intents)
                }
                .setEventDispatcher(
                    EventDispatcher.builder()
                        .eventScheduler(DiscordTaskPool.discordScheduler)
                        .build()
                )
            val gateway = checkNotNull(discord.login().awaitSingle())

            val self = gateway.self.awaitSingle()

            // send commands for instance
            val commands = globalCommands.toMutableList()
            if(instance.musicEnabled) commands.addAll(musicCommands)
            if(instance.starboardEnabled) commands.add(starboardCommand)

            val rest = gateway.rest()
            val appId = checkNotNull(rest.applicationId.block())
            try {
                rest.applicationService
                    .bulkOverwriteGlobalApplicationCommand(appId, commands)
                    .then()
                    .awaitAction()
            } catch(e: Exception) {
                LOG.error("Error updating application commands: ${e.message}")
            }

            @Suppress("DEPRECATION") // discriminators are still valid for bots
            val fbk = FBK(instance.id, gateway, self.username, self.discriminator ?: "0", InstanceProperties(instance))
            val offlineChecks = gateway.guilds
                .flatMap { guild ->
                    mono {
                        delay(Duration.ofSeconds(2))
                        OfflineUpdateHandler.runChecks(fbk.clientId, guild)
                        InviteWatcher.updateGuild(fbk.clientId, guild)
                        //RecoverQueue.recover(fbk, guild)
                    }
                }
                .onErrorResume { t ->
                    LOG.error("Error in offline check: ${t.message}")
                    LOG.warn(t.stackTraceString)
                    Mono.empty()
                }
            publishers.add(offlineChecks)

            val allListeners = eventListeners.map { listener ->
                gateway
                    .on(listener.eventType.java, listener::wrapAndHandle)
                    .onErrorResume { t ->
                        LOG.error("Uncaught error in event handler: ${t.message}")
                        LOG.warn(t.stackTraceString)
                        Mono.empty()
                    }
            }
            publishers.addAll(allListeners)

            instance.id to fbk
        }

        services = ServiceWatcherManager(this)

        return publishers
    }

    suspend fun logout() {
        instances.values
            .forEach { fbk ->
                fbk.client.logout().awaitAction()
            }
    }
}