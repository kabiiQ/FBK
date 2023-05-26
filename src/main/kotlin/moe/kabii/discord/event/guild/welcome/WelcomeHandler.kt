package moe.kabii.discord.event.guild.welcome

import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.WelcomeSettings
import moe.kabii.discord.event.EventListener
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.success

class WelcomerListener(val instances: DiscordInstances) : EventListener<MemberJoinEvent>(MemberJoinEvent::class) {

    override suspend fun handle(event: MemberJoinEvent) {
        val fbk = instances[event.client]
        val guildId = event.guildId.asLong()
        val config = GuildConfigurations.getOrCreateGuild(fbk.clientId, guildId)
        val channelId = config.welcomer.channelId ?: return
        val welcomer = if(config.welcomer.anyElements(guildId)) config.welcomer else WelcomeSettings(channelId = channelId)

        val welcome = try {
            val welcomeMessage = WelcomeMessageFormatter.createWelcomeMessage(guildId, welcomer, event.member)
            event.client
                .getChannelById(channelId.snowflake)
                .ofType(GuildMessageChannel::class.java)
                .flatMap { chan -> chan.createMessage(welcomeMessage) }
                .awaitSingle()
        } catch(ce: ClientException) {
            val err = ce.status.code()
            LOG.warn("Unable to send Welcome message to channel $channelId: ${ce.message}. Disabling feature in channel. WelcomeHandler.java")
            when(err) {
                404 -> {
                    // channel deleted
                    config.welcomer.channelId = null
                    config.save()
                }
                403 -> {
                    // permission denied
                    // TODO pdenied
//                    config.welcomer.channelId = null
//                    config.save()
//                    val message = "I tried to send a **welcome** message but I am missing permission to send messages+embeds/files in <#$channelId>. The **welcome** channel has been automatically disabled.\nOnce permissions are corrected, you can run **/welcome channel <#$channelId>** to re-enable the welcomer."
//                    TrackerUtil.notifyOwner(fbk, event.guildId.asLong(), message)
                }
                else -> throw ce
            }
            null
        }

        // add a reaction, if configured
        if(welcome != null && welcomer.emoji != null) {
            welcome.addReaction(welcomer.emoji!!.toReactionEmoji()).success().awaitSingle()
        }
    }
}