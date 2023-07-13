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

    object GuildInfo : Command("server") {
        override val wikiPath = "Discord-Info-Commands#get-server-info-with-server"

        init {
            chat {
                val (targetClient, targetGuild) = args.optStr("id")
                    ?.toLongOrNull()?.snowflake
                    ?.run {
                        val fbk = handler.instances.getByGuild(this).firstOrNull()
                        val guild = fbk?.client?.getGuildById(this)?.tryAwait()?.orNull()
                        if(guild != null) return@run fbk to guild
                        null
                    }
                    ?: (client to target)

                val instanceId = targetClient.clientId
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

                val info = "This Discord server was created $creation.\n\nServiced by FBK instance #$instanceId (${targetClient.username}#${targetClient.discriminator})."

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

                ereply(
                    Embeds.fbk(info)
                        .withAuthor(EmbedCreateFields.Author.of(targetGuild.name, null, targetGuild.getIconUrl(Image.Format.PNG).orNull()))
                        .withFields(fields)
                ).awaitSingle()
            }
        }
    }
}