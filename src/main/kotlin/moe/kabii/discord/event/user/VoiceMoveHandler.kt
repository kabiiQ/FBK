package moe.kabii.discord.event.user

import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.VoiceChannel
import discord4j.core.event.domain.VoiceStateUpdateEvent
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.TempStates
import moe.kabii.data.mongodb.FeatureChannel
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.LogSettings
import moe.kabii.discord.command.logColor
import moe.kabii.discord.event.EventHandler
import moe.kabii.rusty.Ok
import moe.kabii.structure.*
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux

object VoiceMoveHandler : EventHandler<VoiceStateUpdateEvent>(VoiceStateUpdateEvent::class) {
    override suspend fun handle(event: VoiceStateUpdateEvent) { // voicelog
        val oldState = event.old.orNull()
        val newState = event.current
        // at this time we only care if the channel changes - this event triggers on mutes, etc.
        if(newState.channelId.orNull() == oldState?.channelId?.orNull()) {
            return
        }

        val newChannel = newState.channel.tryAwait().orNull()

        val oldChannel = oldState?.run {
            channel.tryAwait().orNull()
        }
        val old = oldChannel != null
        val new = newChannel != null


        val config = GuildConfigurations.getOrCreateGuild(newState.guildId.asLong())
        val user = newState.user.awaitSingle()
        val guildID = newState.guildId

        val member = user.asMember(guildID).tryAwait().orNull()

        // voice logs
        config.options.featureChannels.values.toList()
            .filter(FeatureChannel::logChannel)
            .map(FeatureChannel::logSettings)
            .filter(LogSettings::voiceLog)
            .filter { log -> log.shouldInclude(user) }
            .toFlux().flatMap { voiceLog ->
                val status = when {
                    new && !old -> "Connected to voice channel ``${newChannel!!.name}``"
                    !new && old -> "Disconnected from voice channel ``${oldChannel!!.name}``"
                    new && old -> "Moved from channel ``${oldChannel!!.name}`` -> ``${newChannel!!.name}``"
                    else -> return@flatMap Mono.empty<Message>()
                }

                event.client.getChannelById(voiceLog.channelID.snowflake)
                    .ofType(MessageChannel::class.java)
                    .flatMap { chan ->
                        chan.createEmbed { embed ->
                            embed.setAuthor("${user.username}#${user.discriminator}", null, user.avatarUrl)
                            embed.setDescription(status)
                            logColor(member, embed)
                        }
                    }
            }.subscribe()

        // temporary voice channel listeners
        if(old) {
            val temp = config.tempVoiceChannels.tempChannels
            val oldID = oldChannel!!.id.asLong()
            if(temp.contains(oldID)) {
                if(oldChannel.voiceStates.hasElements().awaitFirstOrNull() != true) {
                    temp.remove(oldID)
                    config.save()
                    oldChannel.delete("Empty temporary channel.").success().awaitSingle()
                }
            }
        }

        // actions when the bot is moved
        if(event.current.userId == event.client.selfId.get()) {
            config.musicBot.lastChannel = newChannel?.id?.asLong()
            config.save()

            if(old && new && TempStates.dragGuilds.removeIf(guildID::equals)) {
                // if guild has enabled drag command and the bot is moved, move all users along with the bot
                oldState!!.channel
                    .flatMapMany(VoiceChannel::getVoiceStates)
                    .flatMap(VoiceState::getUser)
                    .flatMap { user -> user.asMember(guildID) }
                    .flatMap { member ->
                        member.edit { spec ->
                            spec.setNewVoiceChannel(newChannel!!.id)
                        }
                    }.subscribe()
            }
        }

        // autorole
        // find applicable autoroles for new channel

        if(!user.isBot) {
            val autoRoles = config.autoRoles.voiceConfigurations
            val rolesNeeded = if (new) { // has a current channel, may need auto roles
                autoRoles.filter { cfg ->
                    if (cfg.targetChannel == null) true else { // if targetChannel is null then this autorole applies to any voice channel
                        cfg.targetChannel == newChannel!!.id.asLong()
                    }
                }
            } else emptyList() // no current channel, no roles needed

            val guild = newState.guild.awaitSingle()
            val eventMember = member ?: return // kicked
            rolesNeeded.toFlux()
                .filter { cfg -> !eventMember.roleIds.contains(cfg.role.snowflake) } // add missing roles
                .map { missingRole -> missingRole.role.snowflake }
                .collectList().awaitSingle()
                .forEach { targetRole ->
                    // check if roles still exist first
                    val cfgRole = guild.getRoleById(targetRole).tryBlock()
                    if (cfgRole is Ok) {
                        eventMember.addRole(cfgRole.value.id).subscribe()
                    } else {
                        if (autoRoles.removeIf { cfg -> cfg.role == targetRole.asLong() }) {
                            config.save()
                        }
                    }
                }

            eventMember.roleIds.toFlux() // remove extra autoroles
                .filter { roleID -> autoRoles.find { cfg -> cfg.role.snowflake == roleID } != null } // any autorole the user has
                .filter { roleID -> rolesNeeded.find { cfg -> cfg.role.snowflake == roleID } == null } // which is not currently needed
                .flatMap(eventMember::removeRole)
                .onErrorResume { _ -> Mono.empty() } // don't care about race conditions against other users/events. if the role is gone we don't worry about it.
                .subscribe()
        }
    }
}