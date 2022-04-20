package moe.kabii.discord.event.user

import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.DiscordInstances
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.LogSettings
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.BotUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.logColor
import moe.kabii.rusty.Ok
import moe.kabii.util.extensions.*
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux

class VoiceUpdateListener(val instances: DiscordInstances) : EventListener<VoiceStateUpdateEvent>(VoiceStateUpdateEvent::class) {
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


        val client = instances[event.client]
        val config = GuildConfigurations.getOrCreateGuild(client.clientId, newState.guildId.asLong())
        val user = newState.user.awaitSingle()
        val guildID = newState.guildId

        val member = user.asMember(guildID).tryAwait().orNull()

        // voice logs
        config.logChannels()
            .filter(LogSettings::voiceLog)
            .filter { log -> log.shouldInclude(user) }
            .forEach { targetLog ->
                val status = when {
                    new && !old -> "Connected to voice channel `${newChannel!!.name}`"
                    !new && old -> "Disconnected from voice channel `${oldChannel!!.name}`"
                    new && old -> "Moved from channel `${oldChannel!!.name}` -> `${newChannel!!.name}`"
                    else -> return@forEach
                }

                val logMessage = event.client
                    .getChannelById(targetLog.channelID.snowflake)
                    .ofType(MessageChannel::class.java)
                    .flatMap { logChan ->
                        logChan.createMessage(
                            Embeds.other(status, logColor(member))
                                .withAuthor(EmbedCreateFields.Author.of(user.userAddress(), null, user.avatarUrl))
                        )
                    }

                try {
                    logMessage.awaitSingle()
                } catch (ce: ClientException) {
                    val err = ce.status.code()
                    if(err == 404 || err == 403) {
                        // channel is deleted or we don't have send message perms. remove log configuration
                        LOG.info("Unable to send voice state log for channel '${targetLog.channelID}'. Disabling voicelog.")
                        LOG.debug(ce.stackTraceString)
                        targetLog.voiceLog = false
                        config.save()
                    } else throw ce
                }
            }

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
        if(event.current.userId == event.client.selfId) {
            config.musicBot.lastChannel = newChannel?.id?.asLong()
            config.save()
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
            val audio = AudioManager.getGuildAudio(client, guildID.long)
            when(alone) {
                true -> {
                    // bot is alone - schedule a disconnection
                    audio.discord.startTimeout()
                }
                false -> {
                    // someone is in the bot channel. if audio is being played, cancel any timeouts
                    if(audio.player.playingTrack != null || audio.queue.isNotEmpty()) {
                        audio.discord.cancelPendingTimeout()
                    } // otherwise, let the timeout continue. we leave the vc if not in use
                }
                null -> Unit // the bot is not in a voice channel (empty)
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