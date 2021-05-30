package moe.kabii.command.commands.voice

import discord4j.core.`object`.PermissionOverwrite
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.success

object TemporaryChannels : CommandContainer {
    object CreateTempChannel : Command("temp", "tempchannel", "createtemp", "createtempchannel", "temporarychannel", "tempchan", "temporarychan") {
        override val wikiPath = "Utility-Commands#temporary-voice-channels"

        init {
            botReqs(Permission.MOVE_MEMBERS, Permission.MANAGE_CHANNELS)
            discord {
                // feature must be manually enabled at this time. not likely to be something server owners want without knowing
                channelFeatureVerify(FeatureChannel::tempChannelCreation, "temp")
                // user must be in a voice channel so they can be moved immediately into the temp channel, then record the channel
                val voice = member.voiceState.flatMap { voice -> voice.channel }.awaitFirstOrNull()
                if(voice == null) {
                    error("You must be in a voice channel in order to create a temporary channel.").awaitSingle()
                    return@discord
                }

                val channelName = if(args.isEmpty()) "${member.displayName}'s channel" else noCmd
                val categoryID = voice.categoryId.orNull()
                val ownerPermissions = setOf(
                    PermissionOverwrite.forMember(member.id,
                        PermissionSet.of(Permission.VIEW_CHANNEL, Permission.MOVE_MEMBERS, Permission.MANAGE_CHANNELS, Permission.CONNECT, Permission.SPEAK, Permission.PRIORITY_SPEAKER), // granted
                        PermissionSet.none()) // denied
                )

                val newChannel = target.createVoiceChannel { vc ->
                    vc.reason = "${author.username} (${author.id.asString()}) self-created temporary voice channel."
                    vc.setName(channelName)
                    if(categoryID != null) vc.setParentId(categoryID)
                    vc.setPermissionOverwrites(ownerPermissions)
                }.awaitSingle()

                try {
                    member.edit { member -> member.setNewVoiceChannel(newChannel.id) }.awaitSingle()
                } catch(ce: ClientException) {
                    error("Unable to move user into their temporary channel. Please check my permissions within this category.").awaitSingle()
                    newChannel.delete("Unable to move user into their temporary channel.").success().awaitSingle()
                    return@discord
                }
                config.tempVoiceChannels.tempChannels.add(newChannel.id.asLong())
                config.save()
                embed("Temporary voice channel created: **${newChannel.name}**. This channel will exist until all users leave the channel.").awaitSingle()
            }
        }
    }
}