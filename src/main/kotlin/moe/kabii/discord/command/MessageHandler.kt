package moe.kabii.discord.command

import com.github.twitch4j.TwitchClient
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import com.github.twitch4j.common.enums.CommandPermission
import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Permission
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.launch
import moe.kabii.data.TempStates
import moe.kabii.data.mongodb.FeatureChannel
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.TwitchConfig
import moe.kabii.data.relational.MessageHistory
import moe.kabii.discord.conversation.Conversation
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.Executors
import kotlin.reflect.KClass

class MessageHandler(private val twitch: TwitchClient) {
    private val globalPrefix = ";;"
    private val commandsDiscord = mutableListOf<Command>()
    private val commandsTwitch = mutableListOf<Command>()

    val commands: List<Command> by lazy { commandsDiscord + commandsTwitch }

    private val commandContext = Executors.newFixedThreadPool(10).asCoroutineScope()

    infix fun register(container: CommandContainer) {
        container::class.nestedClasses
            .map(KClass<*>::objectInstance)
            .filterIsInstance<Command>()
            .forEach { cmd ->
                register(cmd)
                "Registered command \"${cmd.baseName}\". Aliases: ${cmd.aliases.joinToString("/")}. Object: ${cmd::class.simpleName}".println()
            }
    }

    fun register(command: Command) {
        if(command.executeDiscord != null) commandsDiscord.add(command)
        if(command.executeTwitch != null) commandsTwitch.add(command)
    }

    fun handleDiscord(event: MessageCreateEvent) {
        var content = event.message.content.orNull()

        // only embeds, files, skip any further processing at this time
        if (content.isNullOrBlank()) {
            return
        }

        val config = event.guildId.map { id -> GuildConfigurations.getOrCreateGuild(id.asLong()) }.orNull()
        val msgArgs = content.split(" ")

        if (config != null) {
            // log messages if this server has an edit/delete log
            val log = config.logChannels()
                .map(FeatureChannel::logSettings)
                .filter { chan -> chan.editLog || chan.deleteLog }
                .any()
            if(log) {
                transaction {
                    MessageHistory.Message.new(event.guildId.get().asLong(), event.message)
                }
            }
        }

        // DISCORD COMMAND HANDLER
        // this sets the prefix for PMs, as long as the guild is legitimate the default prefix is set in GuildConfiguration
        val prefix = config?.prefix ?: ";"
        val cmdStr = if (msgArgs[msgArgs.size - 1].equals("desu", true)) {
            content = content.substring(0, content.length - 4)
            msgArgs[0]
        } else if (msgArgs[0].startsWith(prefix)) {
            msgArgs[0].substring(prefix.length)
        } else if(msgArgs[0].startsWith(globalPrefix)) {
            msgArgs[0].substring(globalPrefix.length)
        } else null

        val author = event.message.author.orNull() ?: return
        if (cmdStr != null) {
            if(config != null) {
                // dummy command listener
                config.commands.commands.find { dummy -> dummy.command == cmdStr.toLowerCase() }?.run {
                    if (!restrict || event.member.get().hasPermissions(Permission.MANAGE_MESSAGES)) {
                        event.message.channel
                            .flatMap { chan -> chan.createMessage(response) }
                            .subscribe()
                    }
                }

                // role command listener
                config.selfRoles.roleCommands.toMap().entries.find { (command, _) -> cmdStr.toLowerCase() == command }
                    ?.let { (command, role) ->
                        commandContext.launch {
                            val guildRole =
                                event.guild.flatMap { guild -> guild.getRoleById(role.snowflake) }.tryBlock()
                            when(guildRole) {
                                is Err -> {
                                    if(guildRole.value is ClientException) {
                                        config.selfRoles.roleCommands.remove(command)
                                        config.save()
                                    }
                                }
                                is Ok -> {
                                    val member = event.member.get()
                                    member.addRole(role.snowflake).block()
                                    event.message.channel.flatMap { chan ->
                                        chan.createEmbed { spec ->
                                            kizunaColor(spec)
                                            spec.setAuthor("${member.username}#${member.discriminator}", null, member.avatarUrl)
                                            spec.setDescription("You have been given the **${guildRole.value.name}** role.")
                                        }
                                    }.subscribe()
                                }
                            }
                        }
                }
            }

            val command = commandsDiscord.find { it.aliases.contains(cmdStr.toLowerCase()) }
            if (command != null) {
                val isPM = !event.guildId.isPresent
                // command parameters
                commandContext.launch {
                    val enabled = if(isPM) true else config!!.commandFilter.isCommandEnabled(command)
                    if(!enabled) return@launch
                    val guild = event.guild.block()
                    val targetID = (if(isPM) author.id else guild.id).asLong()
                    val username = author.username
                    val guildName = if(isPM) username else guild.name
                    val context = if (isPM) "Private" else "Guild"
                    "${context}Message#${event.message.id.asLong()}:\t$guildName:\t$username:\t$content".println()
                    "Executing command ${command.baseName} on ${Thread.currentThread().name}".println()
                    val noCmd = content.substring(msgArgs[0].length).trim()
                    val args = noCmd.split(" ").filter(String::isNotBlank)
                    val chan = event.message.channel.block()
                    val param = DiscordParameters(this@MessageHandler, event, chan, guild, author, isPM, noCmd, args, command, cmdStr, twitch)

                    try {
                        if (command.executeDiscord != null)
                            command.executeDiscord!!(param)
                    } catch (parse: GuildTargetInvalidException) {
                        param.error("${parse.string} Execute this command while in a guild channel or first use the **setguild** command to set your desired guild target.").subscribe()
                    } catch (perms: MemberPermissionsException) {
                        val s = if(perms.perms.size > 1) "s" else ""
                        val reqs = perms.perms.joinToString(", ")
                        param.error("The **${param.alias}** command is restricted. (Requires the **$reqs** permission$s).").subscribe()
                    } catch (feat: FeatureDisabledException) {
                        param.error("The **${feat.feature}** feature is not enabled in this channel").subscribe()
                    } catch (ce: ClientException) {
                        // bot is missing permissions
                        when (ce.status.code()) {
                            403 -> {
                                if (config == null || chan !is TextChannel) return@launch
                                if (ce.errorResponse.fields["string"]?.equals("Missing Permissions") != true) return@launch
                                val botPermissions = chan.getEffectivePermissions(event.client.selfId.get()).block()
                                val listMissing = command.discordReqs
                                        .filterNot(botPermissions::contains)
                                        .joinToString("\n")
                                author.privateChannel
                                        .flatMap { pm ->
                                            pm.createEmbed { spec ->
                                                spec.setDescription("Configuration alert: you tried to use **${command.baseName}** in channel ${chan.mention} but I am missing required permissions\n\n**$listMissing\n**")
                                            }
                                        }.subscribe()
                            }
                            else -> {
                                println("Uncaught client error in command ${command.baseName} on guild $targetID: ${ce.message}")
                                ce.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        println("Uncaught exception in command ${command.baseName} on guild $targetID: ${e.message}")
                        println("Erroring command: $content")
                        e.printStackTrace()
                    }
                }
            }
            return
        }
        // DISCORD CONVERSATION CALLBACKS
        Conversation.conversations.find { conversation ->
            conversation.criteria.channel == event.message.channelId.asLong()
                    &&  conversation.criteria.user == author.id.asLong()
        }?.test(content)
    }

    fun handleTwitch(event: ChannelMessageEvent) {
        // getGuild discord guild
        val msgArgs = event.message.split(" ").filterNot { it.isBlank() }
        val isMod = event.permissions.contains(CommandPermission.MODERATOR)

        println("TwitchMessage#${event.channel.name}:\t${event.user.name}:\t${event.message}")

        // discord-twitch verification- check all messages even if not linked guild
        val verification = TempStates.twitchVerify.entries.find { (id, config) ->
            id == event.channel.id
        }
        if (verification != null) {
            if (event.message.trim().toLowerCase().startsWith(";verify") && event.permissions.contains(CommandPermission.MODERATOR)) {
                val targetConfig = verification.value
                targetConfig.options.linkedTwitchChannel = TwitchConfig(verification.key)
                targetConfig.save()
                TempStates.twitchVerify.remove(verification.key)
                event.reply("Chat linked! Hello :)")
            }
            // always return, don't process normal commands if server is not verified
            return
        }

        val guild = GuildConfigurations.getGuildForTwitch(event.channel.id)
        if(guild == null) return // shouldn't happen but if we are in non-verified channel, ignore the message

        // dummy command handling
        guild.commands.commands.find { it.command == msgArgs[0] }?.run {
            if (!restrict || isMod) {
                event.reply(response)
            }
        }

        // twitch command handling
        // if the discord guild has a custom prefix we use that
        val prefix = guild.prefix ?: ";"
        val cmdStr = if (msgArgs[0].startsWith(prefix)) {
            msgArgs[0].substring(prefix.length)
        } else null
        if (cmdStr != null) {
            val command = commandsTwitch.find { it.aliases.contains(cmdStr.toLowerCase()) }
            if (command != null) {
                commandContext.launch {
                    val noCmd = event.message.substring(msgArgs[0].length).trim()
                    val args = noCmd.split(" ").filter { it.isNotBlank() }
                    val param = TwitchParameters(event, noCmd, guild, isMod, args)
                    try {
                        if (command.executeTwitch != null) {
                            command.executeTwitch!!(param)
                        }
                    } catch (e: Exception) {
                        println("Uncaught exception in Twitch command ${command.baseName}")
                        println("Erroring command: ${event.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}