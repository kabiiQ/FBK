package moe.kabii.instances

import discord4j.common.util.Snowflake
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import discord4j.gateway.intent.IntentSet
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.command.CommandManager
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.documentation.CommandDocumentor
import moe.kabii.command.registration.GlobalCommandRegistrar
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.event.bot.NewGuildListener
import moe.kabii.discord.event.bot.ReconnectListener
import moe.kabii.discord.event.guild.*
import moe.kabii.discord.event.guild.welcome.WelcomerListener
import moe.kabii.discord.event.interaction.*
import moe.kabii.discord.event.message.starboard.StarboardEventHandler
import moe.kabii.discord.event.user.MemberUpdateListener
import moe.kabii.discord.event.user.VoiceUpdateListener
import moe.kabii.discord.invite.InviteWatcher
import moe.kabii.discord.tasks.OfflineUpdateHandler
import moe.kabii.discord.util.Uptime
import moe.kabii.trackers.ServiceWatcherManager
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.stackTraceString
import org.reflections.Reflections
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

class FBK(
    val clientId: Int,
    val client: GatewayDiscordClient,
    val username: String,
    val discriminator: String
) {
    val uptime = Uptime()
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
        val globalCommands = GlobalCommandRegistrar.getAllGlobalCommands(modules)

        // generate command self-documentation
        CommandDocumentor.writeCommandDoc(manager, globalCommands)
        LOG.info("Command List.md updated.")

        val instancedListeners = listOf(
            ChatCommandHandler(this),
            UserCommandHandler(this),
            MessageCommandHandler(this),
            AutoCompleteHandler(this),
            ReconnectListener(this),
            NewGuildListener(this),
            ChannelDeletionListener(this),
            RoleDeletionListener(this),
            MemberUpdateListener(this),
            WelcomerListener(this),
            VoiceUpdateListener(this),
            BanLogger.BanListener(this),
            BanLogger.PardonListener(this),
            JoinLogger.JoinListener(this),
            PartLogger.PartListener(this),
            ReactionRoleHandler.ReactionAddListener(this),
            ReactionRoleHandler.ReactionRemoveListener(this),
            StarboardEventHandler.ReactionAddListener(this),
            StarboardEventHandler.ReactionRemoveListener(this),
            StarboardEventHandler.ReactionEmojiRemoveListener(this),
            StarboardEventHandler.ReactionBulkRemoveListener(this),
            StarboardEventHandler.MessageDeletionListener(this),
            ButtonRoleHandler(this)
        )

        val eventListeners = reflection.getSubTypesOf(EventListener::class.java)
            .mapNotNull { clazz ->
                clazz.kotlin.objectInstance?.apply {
                    LOG.debug("Registering EventHandler: $eventType :: $clazz")
                }
            }

        val publishers = mutableListOf<Flux<*>>()

        // load FBK instances from json and connect each
        instances = InstanceDataLoader.loadFromFile().map { instance ->

            // connect instance to discord
            val discord = DiscordClientBuilder
                .create(instance.token)
                .build()
                .gateway()
                .setEnabledIntents(IntentSet.all())
            val gateway = checkNotNull(discord.login().awaitSingle())

            val self = gateway.self.awaitSingle()

            // send commands for instance
            val rest = gateway.rest()
            val appId = checkNotNull(rest.applicationId.block())
            rest.applicationService
                .bulkOverwriteGlobalApplicationCommand(appId, globalCommands)
                .then()
                .awaitAction()

            val fbk = FBK(instance.id, gateway, self.username, self.discriminator)
            val offlineChecks = gateway.guilds
                .flatMap { guild ->
                    mono {
                        delay(Duration.ofSeconds(2))
                        OfflineUpdateHandler.runChecks(fbk.clientId, guild)
                        InviteWatcher.updateGuild(fbk.clientId, guild)
                        // RecoverQueue.recover(fbk, guild) TODO re-enable
                    }
                }
                .onErrorResume { t ->
                    LOG.error("Error in offline check: ${t.message}")
                    LOG.warn(t.stackTraceString)
                    Mono.empty()
                }
            publishers.add(offlineChecks)

            val allListeners = (instancedListeners + eventListeners).map { listener ->
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
        }.toMap()

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