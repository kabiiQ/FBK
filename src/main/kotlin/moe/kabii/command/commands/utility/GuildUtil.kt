package moe.kabii.command.commands.utility

import discord4j.common.util.TimestampFormat
import discord4j.core.`object`.entity.Guild
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.util.Image
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.Embeds
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

                val fields = mutableListOf<EmbedCreateFields.Field>(
                    EmbedCreateFields.Field.of("Server Owner", owner, true),
                    EmbedCreateFields.Field.of("Member Count", memberCount, true),
                    EmbedCreateFields.Field.of("Channel Count", channelCount, true),
                    EmbedCreateFields.Field.of("Emoji Count", emojiCount, true),
                    EmbedCreateFields.Field.of("Server ID", serverID, true),
                    EmbedCreateFields.Field.of("Default Notifications", notif, true)
                )

                if(features.isNotEmpty()) {
                    val featureList = "\n${features.joinToString("\n")}"
                    fields.add(EmbedCreateFields.Field.of("Additional Features:", featureList, true))
                }
                if(boosts > 0) {
                    fields.add(EmbedCreateFields.Field.of("Server Boosters", boosts.toString(), true))
                    fields.add(EmbedCreateFields.Field.of("Boost Level", targetGuild.premiumTier.value.toString(), true))
                }
                if(mfa == Guild.MfaLevel.ELEVATED) fields.add(EmbedCreateFields.Field.of("Admin 2FA Required", "true", false))
                if(description != null) fields.add(EmbedCreateFields.Field.of("Description", description, false))

                reply(
                    Embeds.fbk(more.toString())
                        .withAuthor(EmbedCreateFields.Author.of(targetGuild.name, null, targetGuild.getIconUrl(Image.Format.PNG).orNull()))
                        .withFields(fields)
                ).awaitSingle()
            }
        }
    }
}