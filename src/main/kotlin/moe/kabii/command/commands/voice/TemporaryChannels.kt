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
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.success

object TemporaryChannels : CommandContainer {
    object CreateTempChannel : Command("temp") {
        override val wikiPath = "Utility-Commands#temporary-voice-channels"

        init {
            botReqs(Permission.MOVE_MEMBERS, Permission.MANAGE_CHANNELS, Permission.CONNECT, Permission.SPEAK)
            chat {
                // feature must be manually enabled at this time. not likely to be something server owners want without knowing
                channelFeatureVerify(FeatureChannel::tempChannelCreation, "tempvc", allowOverride = false)
                // user must be in a voice channel so they can be moved immediately into the temp channel, then record the channel
                val voice = member.voiceState.flatMap { voice -> voice.channel }.awaitFirstOrNull()
                if(voice == null) {
                    ereply(Embeds.error("You must be in a voice channel in order to create a temporary channel.")).awaitSingle()
                    return@chat
                }

                val channelName = args.optStr("name") ?: "${member.displayName}'s channel"
                val categoryID = voice.categoryId.orNull()
                val ownerPermissions = setOf(
                    PermissionOverwrite.forMember(member.id,
                        PermissionSet.of(Permission.VIEW_CHANNEL, Permission.MOVE_MEMBERS, Permission.MANAGE_CHANNELS, Permission.CONNECT, Permission.SPEAK), // granted
                        PermissionSet.none()) // denied
                )

                val newChannel = target.createVoiceChannel(channelName)
                    .withReason("${author.username} (${author.id.asString()}) self-created temporary voice channel.")
                    .withPermissionOverwrites(ownerPermissions)
                    .run { if(categoryID != null) withParentId(categoryID) else this }
                    .awaitSingle()

                try {
                    member.edit().withNewVoiceChannelOrNull(newChannel.id).awaitSingle()
                } catch(ce: ClientException) {
                    ireply(Embeds.error("Unable to move user into their temporary channel. Please check my permissions within this category.")).awaitSingle()
                    newChannel.delete("Unable to move user into their temporary channel.").success().awaitSingle()
                    return@chat
                }
                config.tempVoiceChannels.tempChannels.add(newChannel.id.asLong())
                config.save()
                ireply(Embeds.fbk("Temporary voice channel created: **${newChannel.name}**. This channel will exist until all users leave the channel.")).awaitSingle()
            }
        }
    }
}