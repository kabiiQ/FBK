package moe.kabii.discord.event.user

import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.core.event.domain.VoiceStateUpdateEvent
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.TempStates
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.BotUtil
import moe.kabii.discord.util.logColor
import moe.kabii.rusty.Ok
import moe.kabii.structure.DiscordBot
import moe.kabii.structure.extensions.*
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux

object VoiceUpdateListener : EventListener<VoiceStateUpdateEvent>(VoiceStateUpdateEvent::class) {
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
        config.options.featureChannels.values.asSequence()
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
                            embed.userAsAuthor(user)
                            embed.setDescription(status)
                            logColor(member, embed)
                        }
                    }
            }.subscribe()

        if(old) {
            // temporary voice channel listeners
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
        if(event.current.userId == DiscordBot.selfId) {
            config.musicBot.lastChannel = newChannel?.id?.asLong()
            config.save()

            if(old && new && TempStates.dragGuilds.removeIf(guildID::equals)) {
                // if guild has enabled drag command and the bot is moved, move all users along with the bot
                oldState!!.channel
                    .flatMapMany(VoiceChannel::getVoiceStates)
                    .flatMap(VoiceState::getUser)
                    .flatMap { vcUser -> vcUser.asMember(guildID) }
                    .flatMap { vcMember ->
                        vcMember.edit { spec ->
                            spec.setNewVoiceChannel(newChannel!!.id)
                        }
                    }.subscribe()
            }
        }

        // autorole
        // find applicable autoroles for new channel

        if(!user.isBot) {
            val guild = newState.guild.awaitSingle()
            val autoRoles = config.autoRoles.voiceConfigurations
            val rolesNeeded = if (new) { // has a current channel, may need auto roles
                autoRoles.filter { cfg ->
                    if (cfg.targetChannel == null) true else { // if targetChannel is null then this autorole applies to any voice channel
                        cfg.targetChannel == newChannel!!.id.asLong()
                    }
                }
            } else emptyList() // no current channel, no roles needed

            // check if we should start disconnection timeout
            val alone = BotUtil.getBotVoiceChannel(guild)
                .flatMap(BotUtil::isSingleClient)
                .awaitFirstOrNull()
            val audio = AudioManager.getGuildAudio(guildID.long)
            if(alone == false) {
                // someone is in the bot channel. if audio is being played, cancel any timeouts
                if(audio.player.playingTrack != null || audio.queue.isNotEmpty()) {
                    audio.discord.cancelPendingTimeout()
                } // otherwise, let the timeout continue. we leave the vc if not in use
            } else {
                // bot is alone... schedule a disconnection
                audio.discord.startTimeout()
            }

            val eventMember = member ?: return // kicked
            rolesNeeded.toFlux()
                .filter { cfg -> !eventMember.roleIds.contains(cfg.role.snowflake) } // add missing roles
                .map { missingRole -> missingRole.role.snowflake }
                .collectList().awaitSingle()
                .forEach { targetRole ->
                    // check if roles still exist first
                    val cfgRole = guild.getRoleById(targetRole).tryAwait()
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