package moe.kabii.discord.event.message

import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.*
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.discord.conversation.Conversation
import moe.kabii.discord.util.DiscordBot
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.ServiceWatcherManager
import moe.kabii.util.extensions.*
import org.jetbrains.exposed.sql.transactions.transaction

class MessageHandler(val manager: CommandManager, val services: ServiceWatcherManager) {
    val mention: Regex by lazy {
        val id = DiscordBot.selfId.long
        Regex("<@!?$id>")
    }

    //fun handle(event: MessageCreateEvent) { = mono(manager.context + CoroutineName("MessageHandler") + SupervisorJob()) {}
    fun handle(event: MessageCreateEvent) = manager.context.launch {
        // ignore bots
        if(event.message.author.orNull()?.isBot ?: true) return@launch
        var content = event.message.content

        val config = event.guildId.map { id -> GuildConfigurations.getOrCreateGuild(id.asLong()) }.orNull() // null if pm
        val msgArgs = content.split(" ")

        if (config != null) {
            // log messages if this server has an edit/delete log
            val log = config.logChannels()
                .filter { chan -> chan.editLog || chan.deleteLog }
                .any()
            if(log) {
                transaction {
                    MessageHistory.Message.new(event.message)
                }
            }
        }

        // DISCORD COMMAND HANDLER
        // this sets the prefix for PMs, as long as the guild is legitimate the default prefix is set in GuildConfiguration
        val prefix = config?.prefix ?: GuildConfiguration.defaultPrefix
        val suffix = config?.suffix
        val cmdStr = when {
            suffix != null && msgArgs[msgArgs.size - 1].equals(suffix, ignoreCase = true) -> {
                content = content.substring(0, content.length - suffix.length)
                msgArgs[0]
            }
            msgArgs[0].startsWith(prefix) -> msgArgs[0].substring(prefix.length)
            mention.matches(msgArgs[0]) -> {
                content = content.substring(msgArgs[0].length)
                msgArgs.getOrNull(1)
            }
            else -> null
        }?.lowercase()

        val author = event.message.author.orNull() ?: return@launch
        if (cmdStr != null) {
            if(config != null) {
                // custom command listener
                config.customCommands.commands.find { custom -> custom.command == cmdStr }?.run {
                    if (!restrict || event.member.get().hasPermissions(Permission.MANAGE_MESSAGES)) {
                        event.message.channel
                            .flatMap { chan -> chan.createMessage(response) }
                            .awaitSingle()
                    }
                }

                // role command listener
                config.selfRoles.roleCommands.toMap().entries.find { (command, _) -> cmdStr == command }
                    ?.also { (command, role) ->
                        val guildRole =
                            event.guild.flatMap { guild -> guild.getRoleById(role.snowflake) }.tryAwait()
                        when(guildRole) {
                            is Err -> {
                                val err = guildRole.value
                                if(err is ClientException && err.status.code() == 404) {
                                    config.selfRoles.roleCommands.remove(command)
                                    config.save()
                                }
                            }
                            is Ok -> {
                                val member = event.member.get()
                                member.addRole(role.snowflake).success().awaitSingle()
                                event.message.channel.flatMap { chan ->
                                    chan.createMessage(
                                        Embeds.fbk("You have been given the **${guildRole.value.name}** role.")
                                            .withAuthor(EmbedCreateFields.Author.of(member.userAddress(), null, member.avatarUrl))
                                    )
                                }.awaitSingle()
                            }
                        }
                }
            }

            val command = manager.commandsDiscord[cmdStr]
            if (command != null) {
                val isPM = !event.guildId.isPresent
                // command parameters
                val enabled = if(isPM) true else config!!.commandFilter.isCommandEnabled(command)
                if(!enabled) throw GuildCommandDisabledException(cmdStr)
                val guild = event.guild.awaitFirstOrNull()
                val targetID = (guild?.id ?: author.id).asLong()
                val username = author.username
                val guildName = guild?.name ?: username
                val context = if (isPM) "Private" else "Guild"
                LOG.debug("${context}Message#${event.message.id.asLong()}:\t$guildName:\t$username:\t$content")
                val cmdArgs = content.split(" ")
                val args = cmdArgs
                    .filter(String::isNotBlank)
                    .drop(1)
                    .filterNot { it.equals("@everyone", ignoreCase = true) }
                val noCmd = args.joinToString(" ")
                LOG.info("Executing command ${command.baseName} on ${Thread.currentThread().name}")
                val chan = event.message.channel.awaitSingle()
                val param = DiscordParameters(this@MessageHandler, event, chan, guild, author, noCmd, args, command, cmdStr)

                try {

                    // main command execution
                    if (command.executeDiscord != null)
                        command.executeDiscord!!(param)

                } catch (parse: GuildTargetInvalidException) {
                    param.reply(Embeds.error("${parse.string} Execute this command while in a guild channel or first use the **setguild** command to set your desired guild target.")).subscribe()

                } catch (perms: MemberPermissionsException) {
                    val s = if(perms.perms.size > 1) "s" else ""
                    val reqs = perms.perms.joinToString(", ")
                    param.reply(Embeds.error("The **${param.alias}** command is restricted. (Requires the **$reqs** permission$s).")).subscribe()

                } catch (feat: ChannelFeatureDisabledException) {
                    //val channelMod = feat.origin.member.hasPermissions(feat.origin.guildChan, Permission.MANAGE_CHANNELS)
                    //val enableNotice = if(channelMod) "\nChannel moderators+ can enable this feature using **${prefix}feature ${feat.feature} enable**." else ""
                    val enableNotice = "\nChannel moderators+ can enable this feature using **${prefix}feature ${feat.feature} enable**."

                    val channels = if(feat.listChannels != null) {
                        feat.origin.config.options
                            .getChannels(feat.listChannels).keys
                            .ifEmpty { null }
                            ?.joinToString(", ") { chanId -> "<#$chanId>"}
                    } else null
                    val enabledIn = if(channels != null) "\n**${feat.feature}** is currently enabled in the following channels: $channels"
                    else ""

                    param.reply(Embeds.error("The **${feat.feature}** feature is not enabled in this channel.$enableNotice$enabledIn"))
                        .awaitSingle()

                } catch (guildFeature: GuildFeatureDisabledException) {

                    val serverAdmin = param.member.hasPermissions(guildFeature.enablePermission)
                    val enableNotice = if(serverAdmin) "\nServer staff (${guildFeature.enablePermission.friendlyName} permission) can enable this feature using **${guildFeature.adminEnable}**." else ""
                    param.reply(Embeds.error("The **${guildFeature.featureName}** feature is not enabled in **$guildName**.$enableNotice.")).awaitSingle()

                } catch (cmd: GuildCommandDisabledException) {

                    author.privateChannel
                        .flatMap { pm ->
                            pm.createMessage(
                                Embeds.error("I tried to respond to your command **$cmdStr** in channel ${chan.mention} but that command has been disabled by the staff of **$guildName**.")
                            )
                        }.awaitSingle()

                } catch (ba: BotAdminException) {
                    LOG.info("Bot admin check failed: $param")

                } catch (perms: BotSendMessageException) {
                    LOG.warn("${perms.message} :: channel=${perms.channel}")

                } catch (ce: ClientException) {
                    // bot is missing permissions
                    when (ce.status.code()) {
                        403 -> {
                            LOG.debug("403: ${ce.message}")
                            if (config == null || chan !is GuildChannel) return@launch
                            if (ce.errorResponse.orNull()?.fields?.get("message")?.equals("Missing Permissions") != true) return@launch
                            val botPermissions = chan.getEffectivePermissions(DiscordBot.selfId).awaitSingle()
                            val listMissing = command.discordReqs
                                .filterNot(botPermissions::contains)
                                .map(Permission::friendlyName)
                                .joinToString("\n")
                            author.privateChannel
                                    .flatMap { pm ->
                                        pm.createMessage(
                                            Embeds.error("I tried to respond to your command **${command.baseName}** in channel ${chan.getMention()} but I am missing required permissions:\n\n**$listMissing\n\n**If you think bot commands are intended to be used in this channel, please ask the server's admins to check my permissions.")
                                        )
                                    }.awaitSingle()
                        }
                        else -> {
                            LOG.error("Uncaught client exception in command ${command.baseName} on guild $targetID: ${ce.message}")
                            LOG.debug(ce.stackTraceString) // these can be relatively normal - deleted channels and other weirdness
                        }
                    }
                } catch (e: Exception) {
                    LOG.error("\nUncaught (non-discord) exception in command ${command.baseName} on guild $targetID: ${e.message}\nErroring command: $content")
                    LOG.warn(e.stackTraceString)
                }
            }
            return@launch
        }

        // DISCORD CONVERSATION CALLBACKS
        Conversation.conversations.find { conversation ->
            conversation.criteria.channel == event.message.channelId.asLong()
                    && conversation.criteria.user == author.id.asLong()
        }?.test(content, full = event.message)
    }
}