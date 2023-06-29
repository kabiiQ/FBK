package moe.kabii.ytchat

import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.relational.streams.youtube.ytchat.MembershipConfiguration
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeMembers
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.time.Duration

class YoutubeMembershipMaintainer(val instances: DiscordInstances): Runnable {

    private val repeatTime = Duration.ofDays(1)

    override fun run() {
        applicationLoop {
            LOG.info("Running YT membership DB maintainence")

            propagateTransaction {
                try {

                    // remove any old expired youtube memberships
                    val cutoff = DateTime.now().minusWeeks(3)
                    transaction {
                        YoutubeMembers.deleteWhere {
                            YoutubeMembers.lastUpdate lessEq cutoff
                        }
                    }

                    // pull all users with membership role across servers
                    val configs = MembershipConfiguration.all()
                    for(config in configs) {
                        val discord = instances[config.discordClient].client
                        try {
                            // verify bot is still in guild w/ config
                            val guild = try {
                                discord.getGuildById(config.discordServer.guildID.snowflake).awaitSingle()

                            } catch(e: Exception) {
                                if(e is ClientException) {
                                    if(e.status.code() == 404 || e.status.code() == 403) {
                                        // bot is removed from guild or guild is deleted, remove config
                                        LOG.info("YouTube membership config: unable to access guild ${config.discordServer.guildID}. Deleting config")
                                        config.delete()
                                    }
                                }
                                LOG.debug(e.stackTraceString)
                                continue
                            }

                            val membershipRole = config.membershipRole.snowflake
                            val utils = YoutubeMembershipUtil.forGuild(config.discordClient, guild) ?: continue

                            // get all users with membership role
                            val members = guild.members
                                .filter { member -> member.roleIds.contains(membershipRole) }
                                .collectList().awaitSingle()

                            // remove role from any users that do not have an active membership
                            // active membership = linked yt acc + current membership entry
                            val activeMemberships = utils
                                .getActiveMembers()
                                .map { membership -> membership.discordUser.userID.snowflake }

                            members
                                .filter { member -> !activeMemberships.contains(member.id) }
                                .forEach { inactive ->
                                    inactive.removeRole(membershipRole).success().awaitSingle()
                                }

                        } catch(e: Exception) {
                            LOG.warn("Error processing membership configuration: $config :: ${e.message}")
                            LOG.debug(e.stackTraceString)
                        }
                    }

                } catch(e: Exception) {
                    LOG.warn("Uncaught exception in ${Thread.currentThread().name}/YoutubeMembershipMaintainer :: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }
           delay(repeatTime)
        }
    }
}