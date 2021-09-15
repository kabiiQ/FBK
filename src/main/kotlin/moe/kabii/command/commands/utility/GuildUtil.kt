package moe.kabii.command.commands.utility

import discord4j.common.util.TimestampFormat
import discord4j.core.`object`.entity.Guild
import discord4j.rest.util.Image
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.fbkColor
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait
import moe.kabii.util.extensions.userAddress
import java.time.format.DateTimeFormatter

object GuildUtil : CommandContainer {
    private val formatter = DateTimeFormatter.ofPattern("MMMM dd yyyy @ HH:mm:ss 'UTC'")

    object GuildInfo : Command("server", "serverinfo", "guild", "guildinfo") {
        override val wikiPath = "Discord-Info-Commands#get-server-info"

        init {
            discord {
                val targetGuild = args.getOrNull(0)
                    ?.toLongOrNull()?.snowflake
                    ?.run(event.client::getGuildById)
                    ?.tryAwait()?.orNull()
                    ?: target

                val creation = TimestampFormat.LONG_DATE_TIME.format(targetGuild.id.timestamp)
                val description = targetGuild.description.orNull()
                val guildOwner = targetGuild.owner.tryAwait().orNull()
                val memberCount = targetGuild.memberCount.toString()
                val channelCount = targetGuild.channels.count().tryAwait().orNull()?.toString() ?: "Unavailable"
                val emojiCount = targetGuild.emojiIds.count().toString()
                val serverID = targetGuild.id.asString()
                val mfa = targetGuild.mfaLevel
                val notif = when(targetGuild.notificationLevel) {
                    Guild.NotificationLevel.ALL_MESSAGES -> "All Messages"
                    Guild.NotificationLevel.ONLY_MENTIONS -> "Mentions Only"
                    else -> "Error"
                }
                val boosts = targetGuild.premiumSubscriptionCount.orElse(0)

                val owner = if(guildOwner != null) "${guildOwner.userAddress()}(${guildOwner.id.asString()})" else "Unknown"

                val more = StringBuilder("This Discord server was created $creation.")

                val large = targetGuild.isLarge
                val features = targetGuild.features
                if(large) features.add("LARGE")

                embed {
                    fbkColor(this)
                    setDescription(more.toString())
                    setAuthor(targetGuild.name, null, targetGuild.getIconUrl(Image.Format.PNG).orNull())
                    addField("Server Owner", owner, true)
                    addField("Member Count", memberCount, true)
                    addField("Channel Count", channelCount, true)
                    addField("Emoji Count", emojiCount, true)
                    addField("Server ID", serverID, true)
                    addField("Default Notifications", notif, true)

                    if(features.isNotEmpty()) {
                        val featureList = "\n${features.joinToString("\n")}"
                        addField("Additional Features:", featureList, true)
                    }
                    if(boosts > 0) {
                        addField("Server Boosters", boosts.toString(), true)
                        addField("Boost Level", targetGuild.premiumTier.value.toString(), true)
                    }
                    if(mfa == Guild.MfaLevel.ELEVATED) addField("Admin 2FA Required", "true", false)
                    if(description != null) addField("Description", description, false)
                }.awaitSingle()
            }
        }
    }
}