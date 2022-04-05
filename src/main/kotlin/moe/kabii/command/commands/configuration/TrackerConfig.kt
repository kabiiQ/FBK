package moe.kabii.command.commands.configuration

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.TrackerTarget

object TrackerConfig : CommandContainer {
    object SetDefaultTracker : Command("usetracker") {
        override val wikiPath = "Configuration#overriding-the-default-website-for-track-with-the-usetracker-command"

        init {
            discord {
                // override the tracker that will be used in the current channel if ;track <username> is ran without a channel specified.
                channelVerify(Permission.MANAGE_CHANNELS)

                // /usetracker <tracker name>

                val tracker = TrackerTarget.parseSiteArg(args.int("site"))

                val features = config.getOrCreateFeatures(chan.id.asLong())
                val featureEnabled = tracker.channelFeature.get(features)
                if (!featureEnabled) {
                    ereply(Embeds.error("The **${tracker.full}** tracker is not enabled in **#${guildChan.name}**.")).awaitSingle()
                    return@discord
                }

                features.trackerDefault = tracker.alias.first()
                config.save()
                ireply(Embeds.fbk("The default track target for **#${guildChan.name}** has been set to **${tracker.full}**.")).awaitSingle()
            }
        }
    }
}