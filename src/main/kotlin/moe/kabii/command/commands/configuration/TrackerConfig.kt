package moe.kabii.command.commands.configuration

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.discord.util.Embeds

object TrackerConfig : CommandContainer {
    object SetDefaultTracker : Command("usetracker", "settracker", "usetrack", "settrack", "usefeature", "usetarget", "settarget", "setfeature", "tracker") {
        override val wikiPath = "Configuration#overriding-the-default-website-for-track-with-the-usetracker-command"

        init {
            discord {
                // override the tracker that will be used in the current channel if ;track <username> is ran without a channel specified.
                channelVerify(Permission.MANAGE_CHANNELS)

                // usetracker #channel <tracker name>
                if (args.isEmpty()) {
                    usage("**usetracker** is used to set the default site to track **in this channel** if the **track** command is used without specifying a site.", "usetracker <supported site name>").awaitSingle()
                    return@discord
                }

                val trackerArg = args[0].lowercase()
                val tracker = TargetArguments.declaredTargets.find { supportedSite ->
                    supportedSite.alias.contains(trackerArg)
                }
                if (tracker == null) {
                    reply(Embeds.error("Unknown/unsupported target **${args[0]}**.")).awaitSingle()
                    return@discord
                }

                val features = config.getOrCreateFeatures(chan.id.asLong())
                val featureEnabled = tracker.channelFeature.get(features)
                if (!featureEnabled) {
                    reply(Embeds.error("The **${tracker.full}** tracker is not enabled in **#${guildChan.name}**.")).awaitSingle()
                    return@discord
                }

                features.trackerDefault = tracker.alias.first()
                config.save()
                reply(Embeds.fbk("The default track target for **#${guildChan.name}** has been set to **${tracker.full}**.")).awaitSingle()
            }
        }
    }
}