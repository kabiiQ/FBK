package moe.kabii.discord.command.commands.utility

import discord4j.core.`object`.entity.Guild
import discord4j.rest.util.Image
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.kizunaColor
import moe.kabii.structure.orNull
import moe.kabii.structure.tryAwait
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object GuildUtil : CommandContainer {
    private val formatter = DateTimeFormatter.ofPattern("MMMM dd yyyy @ HH:mm:ss 'UTC'")
    object GuildInfo : Command("server", "serverinfo", "guild", "guildinfo") {
        init {
            discord {
                val createdDateTime = target.id.timestamp.atZone(ZoneOffset.UTC).toLocalDateTime()
                val creation = formatter.format(createdDateTime)
                val description = target.description.orNull()
                val guildOwner = target.owner.tryAwait().orNull()
                val memberCount = target.memberCount.toString()
                val channelCount = target.channels.count().tryAwait().orNull()?.toString() ?: "Unavailable"
                val emojiCount = target.emojiIds.count().toString()
                val serverID = target.id.asString()
                val mfa = target.mfaLevel
                val notif = when(target.notificationLevel) {
                    Guild.NotificationLevel.ALL_MESSAGES -> "All Messages"
                    Guild.NotificationLevel.ONLY_MENTIONS -> "Mentions Only"
                    else -> "Error"
                }
                val region = target.regionId
                val boosts = target.premiumSubscriptionCount.orElse(0)

                val owner = if(guildOwner != null) "${guildOwner.username}#${guildOwner.discriminator} (${guildOwner.id.asString()})" else "Unknown"

                val more = StringBuilder()
                more.append("This guild was created $creation.")

                val large = target.isLarge
                if(large) more.append("\nThis guild is considered \"large\" by Discord.")
                val features = target.features

                embed {
                    kizunaColor(this)
                    setDescription(more.toString())
                    setAuthor(target.name, null, target.getIconUrl(Image.Format.PNG).orNull())
                    addField("Server Owner", owner, true)
                    addField("Member Count", memberCount, true)
                    addField("Channel Count", channelCount, true)
                    addField("Emoji Count", emojiCount, true)
                    addField("Server ID", serverID, true)
                    addField("Default Notifications", notif, true)
                    addField("Voice Region", region, true)

                    if(features.isNotEmpty()) {
                        val featureList = "\n${features.joinToString("\n")}"
                        addField("Additional Features:", featureList, true)
                    }
                    if(boosts > 0) {
                        addField("Server Boosters", boosts.toString(), true)
                        addField("Boost Level", target.premiumTier.value.toString(), true)
                    }
                    if(mfa == Guild.MfaLevel.ELEVATED) addField("Admin 2FA Required", "true", false)
                    if(description != null) addField("Description", description, false)
                }.awaitSingle()
            }
        }
    }
}