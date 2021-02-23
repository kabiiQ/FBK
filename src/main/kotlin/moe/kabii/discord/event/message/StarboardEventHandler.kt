package moe.kabii.discord.event.message

import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.message.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.structure.DiscordBot
import moe.kabii.structure.extensions.orNull
import moe.kabii.util.EmojiCharacters

object StarboardEventHandler {
    object ReactionAddListener : EventListener<ReactionAddEvent>(ReactionAddEvent::class) {
        override suspend fun handle(event: ReactionAddEvent) {
            // ignore self reactions
            if(event.userId == DiscordBot.selfId) return

            val guildId = event.guildId.orNull()?.asLong() ?: return
            // only continue if star reaction
            if(event.emoji.asUnicodeEmoji().filter { reaction -> reaction.raw == EmojiCharacters.star }.orNull() == null) return

            // only continue if guild has a starboard
            val config = GuildConfigurations.getOrCreateGuild(guildId)
            val starboardCfg = config.starboard ?: return

            // only continue if messages are allowed to be starred here (starboarded or starboard itself)
            val channelId = event.channelId.asLong()
            val starboarded = config.options.featureChannels[channelId]?.allowStarboarding ?: true
            if(channelId != starboardCfg.channel) { // if this is not the starboard channel, more checks are required
                if(!starboarded) return
                // if this channel is nsfw, starboard must also be nsfw.
                val nsfw = event.channel.ofType(TextChannel::class.java)
                    .map(TextChannel::isNsfw).awaitSingle()
                if(nsfw && !starboardCfg.includeNsfw) return
            }

            val messageId = event.messageId.asLong()
            // check if this message is already starboarded
            // reactions can be added to either the starboard post itself or the original post
            val existing = starboardCfg.findAssociated(messageId)

            if(existing != null) {
                val addedStar = existing.stars.add(event.userId.asLong())
                if(addedStar) {
                    val guild = event.guild.awaitSingle()
                    val starboard = starboardCfg.asStarboard(guild, config)
                    starboard.updateCount(existing)
                } // else, user already has this message starred
            } else {
                // not already on the starboard, check the new star count
                val message = event.message.awaitSingle()
                val userStars = message.getReactors(event.emoji).collectList().awaitSingle()
                if(userStars.size >= starboardCfg.starsAdd) {
                    // add to the starboard!
                    val guild = event.guild.awaitSingle()
                    val starboard = starboardCfg.asStarboard(guild, config)
                    val stars = userStars.map { user -> user.id.asLong() }.toMutableSet()
                    starboard.addToBoard(message, stars)
                }
            }
        }
    }

    object ReactionRemoveListener : EventListener<ReactionRemoveEvent>(ReactionRemoveEvent::class) {
        override suspend fun handle(event: ReactionRemoveEvent) {
             val guildId = event.guildId.orNull()?.asLong() ?: return
            // only continue if star reaction
            if (event.emoji.asUnicodeEmoji().filter { reaction -> reaction.raw == EmojiCharacters.star } == null) return

            // only continue if guild has a starboard
                val config = GuildConfigurations.getOrCreateGuild(guildId)
                val starboardCfg = config.starboard ?: return

            // only continue if message is starboarded currently
            val messageId = event.messageId.asLong()
            val existing = starboardCfg.findAssociated(messageId) ?: return

            val removedStar = existing.stars.remove(event.userId.asLong())
            if (removedStar) {
                // check if this message should fall off the starboard
                val guild = event.guild.awaitSingle()
                val starboard = starboardCfg.asStarboard(guild, config)
                if(existing.stars.size <= starboardCfg.starsRemove && !existing.exempt) {
                    starboard.removeFromBoard(existing)
                } else {
                    starboard.updateCount(existing)
                }
            }
        }
    }

    object ReactionEmojiRemoveListener : EventListener<ReactionRemoveEmojiEvent>(ReactionRemoveEmojiEvent::class) {
        override suspend fun handle(event: ReactionRemoveEmojiEvent) {
            // triggered when the entire :star: reaction is removed by a moderator
            // in this event, remove the post from the starboard unless configured otherwise
            val guildId = event.guildId.orNull()?.asLong() ?: return

            // only continue if star reaction
            if (event.emoji.asUnicodeEmoji().filter { reaction -> reaction.raw == EmojiCharacters.star } == null) return

            // only continue if guild has a starboard
            val config = GuildConfigurations.getOrCreateGuild(guildId)
            val starboardCfg = config.starboard ?: return

            // only continue if "remove on clear" behavior is enabled
            if(!starboardCfg.removeOnClear) return

            // only continue if message is starboarded currently
            val messageId = event.messageId.asLong()
            val existing = starboardCfg.findAssociated(messageId) ?: return

            val guild = event.guild.awaitSingle()
            val starboard = starboardCfg.asStarboard(guild, config)
            if(!existing.exempt) {
                starboard.removeFromBoard(existing)
            }
        }
    }

    object ReactionBulkRemoveListener : EventListener<ReactionRemoveAllEvent>(ReactionRemoveAllEvent::class) {
        override suspend fun handle(event: ReactionRemoveAllEvent) {
            // triggered when all reactions are removed from a message by a moderator
            // in this event, remove the post from the starboard unless configured otherwise
            val guildId = event.guildId.orNull()?.asLong() ?: return

            // only continue if guild has a starboard
            val config = GuildConfigurations.getOrCreateGuild(guildId)
            val starboardCfg = config.starboard ?: return

            // only continue if "remove on clear" behavior is enabled
            if(!starboardCfg.removeOnClear) return

            // only continue if message is starboarded currently
            val messageId = event.messageId.asLong()
            val existing = starboardCfg.findAssociated(messageId) ?: return

            val guild = event.guild.awaitSingle()
            val starboard = starboardCfg.asStarboard(guild, config)
            if(!existing.exempt) {
                starboard.removeFromBoard(existing)
            }
        }
    }

    object MessageDeletionListener : EventListener<MessageDeleteEvent>(MessageDeleteEvent::class) {
        override suspend fun handle(event: MessageDeleteEvent) {
            // in this event, remove the post from the starboard, unless configured otherwise
            val channel = event.channel.ofType(GuildMessageChannel::class.java).awaitFirstOrNull() ?: return
            val guildId = channel.guildId.asLong()

            // continue if guild has a starboard
            val config = GuildConfigurations.getOrCreateGuild(guildId)
            val starboardCfg = config.starboard ?: return

            // continue if "remove on delete" behavior is enabled
            if(!starboardCfg.removeOnDelete) return

            // continue if message is starboarded
            val messageId = event.messageId.asLong()
            val existing = starboardCfg.findAssociated(messageId) ?: return

            // if "original" message deleted, delete the starboard copy. if starboard message is deleted, just still from db
            val guild = channel.guild.awaitSingle()
            val starboard = starboardCfg.asStarboard(guild, config)
            starboard.removeFromBoard(existing)
        }
    }
}