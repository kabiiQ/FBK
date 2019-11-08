package moe.kabii.discord.command.commands.voice

import discord4j.core.`object`.PermissionOverwrite
import discord4j.core.`object`.entity.Category
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.PermissionSet
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.success
import moe.kabii.structure.tryBlock

object TemporaryChannels : CommandContainer {
    object SetTempChannelCategory : Command("setcategory", "settempcategory") {
        init {
            discord {
                // set the category to create temporary voice channels in
                member.verify(Permission.MANAGE_CHANNELS)
                if(args.isEmpty()) {
                    usage("This command sets the category that temporary voice channels will be created in.", "setcategory <category ID (or \"reset\" to use the default category)>").block()
                    return@discord
                }
                val category = when(val arg = args[0].toLowerCase()) {
                    "reset", "default", "0", "null" -> null
                    else -> {
                        val categoryMatch = Search.channelByID<Category>(this, arg)
                        if(categoryMatch != null) categoryMatch else {
                            usage("No category found matching **$arg**.", "setcategory <category ID (or \"reset\" to use the default category)>").block()
                            return@discord
                        }
                    }
                }
                config.tempVoiceChannels.tempChannelCategory = category?.id?.asLong()
                embed("Temporary voice channels will be created in the **${category?.name ?: "default"}** category.").block()
            }
        }
    }

    object TempChannelCategory : Command("tempcategory", "gettempcategory", "getcategory") {
        init {
            discord {
                member.verify(Permission.MANAGE_CHANNELS)
                val categoryID = config.tempVoiceChannels.tempChannelCategory
                val categoryName = categoryID?.let { id ->
                    target.getChannelById(id.snowflake)
                        .ofType(Category::class.java)
                        .tryBlock().orNull()
                        ?.name ?: "default" // if the category doesn't exist it will fallback to default when used
                }

                embed("Temporary voice channels will be created in the **$categoryName** category.").block()
            }
        }
    }

    object CreateTempChannel : Command("tempchannel", "createtemp", "createtempchannel", "temporarychannel", "tempchan", "temporarychan") {
        init {
            botReqs(Permission.MOVE_MEMBERS, Permission.MANAGE_CHANNELS)
            discord {
                // user must be in a voice channel so they can be moved immediately into the temp channel, then record the channel
                val voice = member.voiceState.flatMap { voice -> voice.channel.hasElement() }
                if(voice.block() != true) {
                    error("You must be in a voice channel in order to be moved into your temporary channel.").block()
                    return@discord
                }

                val channelName = if(args.isEmpty()) "${member.displayName}'s channel" else noCmd
                val categoryID = config.tempVoiceChannels.tempChannelCategory?.snowflake?.let { id ->
                    val category = target.getChannelById(id).ofType(Category::class.java).tryBlock()
                    if(category is Ok) category.value.id else {
                        // if category was deleted, we'll reset it here
                        config.tempVoiceChannels.tempChannelCategory = null; null
                    }
                }
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
                }.block()

                val move = member.edit { member -> member.setNewVoiceChannel(newChannel.id) }.success()

                if(move.block() == true ) {
                    newChannel.delete("Unable to move user into their temporary channel.").subscribe()
                    return@discord
                }
                config.tempVoiceChannels.tempChannels.add(newChannel.id.asLong())
                config.save()
                embed("Temporary voice channel created: **${newChannel.name}**. This channel will exist until all users leave the channel.").block()
            }
        }
    }
}