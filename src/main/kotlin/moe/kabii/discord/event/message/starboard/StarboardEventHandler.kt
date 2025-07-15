package moe.kabii.discord.event.message.starboard

import discord4j.core.event.domain.message.*
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.TextChannel
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.sync.withLock
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.orNull

object StarboardEventHandler {
    class ReactionAddListener(val instances: DiscordInstances) : EventListener<ReactionAddEvent>(ReactionAddEvent::class) {
        override suspend fun handle(event: ReactionAddEvent) {
            // ignore self reactions
            if(event.userId == event.client.selfId) return

            val guildId = event.guildId.orNull()?.asLong() ?: return

            val fbk = instances[event.client]
            if(!fbk.properties.starboardEnabled) return
            // only continue if guild has a starboard
            val config = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId)
            val starboardCfg = config.starboard() ?: return

            // only continue if this guild's starboard emoji
            if(event.emoji != starboardCfg.useEmoji().toReactionEmoji()) return

            // only continue if messages are allowed to be starred here (starboarded or starboard itself)
            val channelId = event.channelId.asLong()
            val starboarded = config.options.featureChannels[channelId]?.allowStarboarding ?: true
            if(channelId != starboardCfg.channel) { // if this is not the starboard channel, more checks are required
                if(!starboarded) return
                // if this channel is nsfw, starboard must also be nsfw.
                val nsfw = event.channel.ofType(TextChannel::class.java)
                    .map(TextChannel::isNsfw).awaitSingleOrNull() ?: false
                if(nsfw && !starboardCfg.includeNsfw) return
            }

            starboardCfg.starsLock.withLock {
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
    }

    class ReactionRemoveListener(val instances: DiscordInstances) : EventListener<ReactionRemoveEvent>(ReactionRemoveEvent::class) {
        override suspend fun handle(event: ReactionRemoveEvent) {
             val guildId = event.guildId.orNull()?.asLong() ?: return
            // only continue if star reaction
            if (event.emoji.asUnicodeEmoji().filter { reaction -> reaction.raw == EmojiCharacters.star } == null) return

            val fbk = instances[event.client]
            if(!fbk.properties.starboardEnabled) return
            // only continue if guild has a starboard
            val config = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId)
            val starboardCfg = config.starboard() ?: return

            starboardCfg.starsLock.withLock {
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
    }

    class ReactionEmojiRemoveListener(val instances: DiscordInstances) : EventListener<ReactionRemoveEmojiEvent>(ReactionRemoveEmojiEvent::class) {
        override suspend fun handle(event: ReactionRemoveEmojiEvent) {
            // triggered when the entire :star: reaction is removed by a moderator
            // in this event, remove the post from the starboard unless configured otherwise
            val guildId = event.guildId.orNull()?.asLong() ?: return

            // only continue if star reaction
            if (event.emoji.asUnicodeEmoji().filter { reaction -> reaction.raw == EmojiCharacters.star } == null) return

            val fbk = instances[event.client]
            if(!fbk.properties.starboardEnabled) return
            // only continue if guild has a starboard
            val config = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId)
            val starboardCfg = config.starboard() ?: return

            // only continue if "remove on clear" behavior is enabled
            if(!starboardCfg.removeOnClear) return

            starboardCfg.starsLock.withLock {
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
    }

    class ReactionBulkRemoveListener(val instances: DiscordInstances) : EventListener<ReactionRemoveAllEvent>(ReactionRemoveAllEvent::class) {
        override suspend fun handle(event: ReactionRemoveAllEvent) {
            // triggered when all reactions are removed from a message by a moderator
            // in this event, remove the post from the starboard unless configured otherwise
            val guildId = event.guildId.orNull()?.asLong() ?: return

            val fbk = instances[event.client]
            if(!fbk.properties.starboardEnabled) return
            // only continue if guild has a starboard
            val config = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId)
            val starboardCfg = config.starboard() ?: return

            // only continue if "remove on clear" behavior is enabled
            if(!starboardCfg.removeOnClear) return

            starboardCfg.starsLock.withLock {
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
    }

    class MessageDeletionListener(val instances: DiscordInstances) : EventListener<MessageDeleteEvent>(MessageDeleteEvent::class) {
        override suspend fun handle(event: MessageDeleteEvent) {
            // in this event, remove the post from the starboard, unless configured otherwise
            val channel = event.channel.awaitFirst() as? GuildMessageChannel ?: return
            val guildId = channel.guildId.asLong()

            val fbk = instances[event.client]
            if(!fbk.properties.starboardEnabled) return
            // continue if guild has a starboard
            val config = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId)
            val starboardCfg = config.starboard() ?: return

            // continue if "remove on delete" behavior is enabled
            if(!starboardCfg.removeOnDelete) return

            starboardCfg.starsLock.withLock {
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
}