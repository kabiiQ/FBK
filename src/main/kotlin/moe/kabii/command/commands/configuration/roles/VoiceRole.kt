package moe.kabii.command.commands.configuration.roles

import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.VoiceConfiguration
import moe.kabii.discord.pagination.PaginationUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Ok
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

object VoiceRole {

    suspend fun createVoiceRole(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        // voice channel target
        val args = subArgs(subCommand)
        val channelTarget = args.optChannel("channel", VoiceChannel::class)?.awaitSingle()

        val configs = config.autoRoles.voiceConfigurations
        val existing = configs.find { cfg -> cfg.targetChannel == channelTarget?.id?.asLong() }
        if(existing != null) {
            val role = target.getRoleById(existing.role.snowflake).tryAwait()
            if(role is Ok) {
                ereply(Embeds.error("There is already an existing auto-role **${role.value.name}** for this channel.")).awaitSingle()
                return@with
            }
            configs.remove(existing)
        }
        val vcName = if(channelTarget != null) "VC-${channelTarget.name}" else "Voice"
        // at this point there was no configuration or we removed the outdated configuration, we can create a new role config
        val newRole = try {
            target.createRole().withName(vcName).awaitSingle()
        } catch(ce: ClientException) {
            if(ce.status.code() == 403) {
                ereply(Embeds.error("I am missing permission to create roles in **${target.name}**.")).awaitSingle()
                return@with
            } else throw ce
        }
        val roleSetup =
            VoiceConfiguration(
                channelTarget?.id?.asLong(),
                newRole.id.asLong()
            )
        configs.add(roleSetup)
        config.save()
        val describe = if(channelTarget == null) "users in any voice channel" else "users in the voice channel **${channelTarget.name}**"
        ireply(Embeds.fbk("Voice auto-role configuration added: $describe will be given the role **${newRole.name}**.")).awaitSingle()
    }

    suspend fun deleteVoiceRole(origin: DiscordParameters, subCommand: ApplicationCommandInteractionOption) = with(origin) {
        val args = subArgs(subCommand)
        val channelTarget = args.optChannel("channel", VoiceChannel::class)?.awaitSingle()

        val autoRoles = config.autoRoles.voiceConfigurations
        val existing = autoRoles.find { cfg -> cfg.targetChannel == channelTarget?.id?.asLong() }
        val channel = if(channelTarget != null) "channel ID **$channelTarget**" else "**all channels**"
        if(existing != null) {
            autoRoles.remove(existing)
            config.save()
            val configRemoved = "Removed automatic voice role from $channel."
            val linkedRole = target.getRoleById(existing.role.snowflake).tryAwait()
            if(linkedRole is Ok) {
                linkedRole.value.delete().tryAwait()
                ireply(Embeds.fbk(configRemoved))
            } else {
                ireply(Embeds.error("$configRemoved Could not find the linked role for this configuration."))
            }.awaitSingle()
        } else {
            ereply(Embeds.error("There is not an existing automatic voice role for $channel.")).awaitSingle()
            return@with
        }
    }

    suspend fun listVoiceRoles(origin: DiscordParameters) = with(origin) {
        member.verify(Permission.MANAGE_ROLES)

        if(config.autoRoles.voiceConfigurations.isEmpty()) {
            ereply(Embeds.error("There are no voice role rules set for **${target.name}**.")).awaitSingle()
            return@with
        }
        val title = "Voice role configuration for **${target.name}**"
        val header = "Users joining the following voice channels will receive the corresponding role."
        val configs = config.autoRoles.voiceConfigurations.toList().map { cfg ->
            // create string describing each config
            val channel = if(cfg.targetChannel != null) {
                target.getChannelById(cfg.targetChannel.snowflake).tryAwait().orNull()
            } else null
            val channelName =
                if(cfg.targetChannel != null)
                    if(channel != null)
                        channel.name
                    else "Channel deleted"
                else "<ANY>"
            val role = target.getRoleById(cfg.role.snowflake).tryAwait()
            val roleName = if(role is Ok) role.value.name else "Role deleted"
            "**Channel:** $channelName **-> Role:** $roleName"
        }
        PaginationUtil.paginateListAsDescription(this, configs, title, header)
    }
}