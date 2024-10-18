package moe.kabii.command.commands.meta

import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.twitter.TwitterMention
import moe.kabii.data.relational.twitter.TwitterMentions
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.data.relational.twitter.TwitterTargetMention
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.and

object MigrationCommand : Command("migration") {
    override val wikiPath: String? = null
    init {
        terminal {

            // migrate from Mention (guild-wide) to TargetMention (channel-specific)
            // using existing Targets as basis for which channels to associate new mention entities with
            LOG.info("Beginning Mention migration")
            propagateTransaction {
                var targets = 0
                var migrated = 0
                TrackedStreams.Target.all()
                    .forEach { target ->
                        targets++
                        // get 'mention' for this target (guild+sc)
                        val guild = target.discordChannel.guild ?: return@forEach
                        val oldMention = TrackedStreams.Mention.find {
                            TrackedStreams.Mentions.streamChannel eq target.streamChannel.id and
                                    (TrackedStreams.Mentions.guild eq guild.id)
                        }.firstOrNull() ?: return@forEach

                        TrackedStreams.TargetMention.new {
                            this.target = target
                            this.mentionRole = oldMention.mentionRole
                            this.mentionRoleMember = oldMention.mentionRoleMember
                            this.mentionRoleUploads = oldMention.mentionRoleUploads
                            this.mentionText = oldMention.mentionText
                            this.lastMention = oldMention.lastMention
                        }
                        migrated++
                    }

                LOG.info("Migration complete\nall targets=$targets, migrated=$migrated")
            }

            LOG.info("Beginning TwitterMention migration")
            propagateTransaction {
                var targets = 0
                var migrated = 0

                TwitterTarget.all()
                    .forEach { target ->
                        targets++
                        // get 'mention' for this twitter target (guild+feed)
                        val guild = target.discordChannel.guild ?: return@forEach
                        val oldMention = TwitterMention.find {
                            TwitterMentions.twitterFeed eq target.twitterFeed.id and
                                    (TwitterMentions.guild eq guild.id)
                        }.firstOrNull() ?: return@forEach

                        TwitterTargetMention.new {
                            this.target = target
                            this.mentionRole = oldMention.mentionRole
                            this.mentionText = oldMention.mentionText
                        }
                        migrated++
                    }

                LOG.info("Migration complete\nall targets=$targets, migrated=$migrated")
            }
        }
    }
}