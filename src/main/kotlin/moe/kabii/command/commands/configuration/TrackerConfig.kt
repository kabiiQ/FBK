package moe.kabii.command.commands.configuration

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.trackers.TargetArguments

object TrackerConfig : CommandContainer {
    object SetDefaultTracker : Command("usetracker", "settracker", "usetrack", "settrack", "usefeature", "usetarget", "settarget", "setfeature") {
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

                val trackerArg = args[0].toLowerCase()
                val tracker = TargetArguments.declaredTargets.find { supportedSite ->
                    supportedSite.alias.contains(trackerArg)
                }
                if (tracker == null) {
                    error("Unknown/unsupported target **${args[0]}**.").awaitSingle()
                    return@discord
                }

                // must be enabled feature in this channel to make any sense
                val features = config.options.featureChannels[chan.id.asLong()]
                if (features == null) {
                    error("There are no trackers enabled in **#${guildChan.name}**.").awaitSingle()
                    return@discord
                }

                val featureEnabled = tracker.channelFeature.get(features)
                if (!featureEnabled) {
                    error("The **${tracker.full}** tracker is not enabled in **#${guildChan.name}**.").awaitSingle()
                    return@discord
                }

                features.defaultTracker = tracker
                config.save()
                embed("The default track target for **#${guildChan.name}** has been set to **${tracker.full}**.").awaitSingle()
            }
        }
    }
}