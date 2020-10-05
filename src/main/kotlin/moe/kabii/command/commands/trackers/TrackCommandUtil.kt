package moe.kabii.command.commands.trackers

import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.rusty.*

object TrackCommandUtil {
    data class TargetArguments(val site: TargetMatch, val accountId: String)

    fun parseTrackTarget(origin: DiscordParameters, inputArgs: List<String>, vararg subset: TargetMatch): Result<TargetArguments, String> = with(origin) {
        // empty 'args' is handled by initial command call erroring and should never occur here
        require(inputArgs.isNotEmpty()) { "Can not parse empty track target" }

        // get the channel features, if they exist. PMs do not require trackers to be enabled
        // thus, a URL or site name must be specified if used in PMs
        val features = if(guild != null) {
            val features = GuildConfigurations.getOrCreateGuild(guild.id.asLong()).options.featureChannels[guildChan.id.asLong()]

            if(features == null) {
                // if this is a guild but the channel never had any features enabled to begin with
                return@with Err("There are no website trackers enabled in **#${guildChan.name}**.")
            } else features
        } else null

        return if(inputArgs.size == 1) {

            // if 1 arg, user supplied just a username OR a url (containing site and username)
            val urlMatch = TargetMatch.values()
                .mapNotNull { supportedSite ->
                    supportedSite.url.find(inputArgs[0])?.to(supportedSite)
                }.firstOrNull()

            if(urlMatch != null) {
                Ok(TargetArguments(
                    site = urlMatch.second,
                    accountId = urlMatch.first.groups[1]?.value!!
                ))
            } else {

                // arg was not a supported url, but there was only 1 arg supplied. check if we are able to assume the track target for this channel
                // simple ;track <username> is not supported for PMs
                if(features == null) {
                    return@with Err("You must specify the site name for tracking in PMs.")
                }

                val default = if(subset.isEmpty()) features.findDefaultTarget() else features.findDefaultTarget(*subset)
                if(default == null) {
                    Err("There are no website trackers enabled in **${guildChan.name}**.")
                } else {
                    Ok(TargetArguments(
                        site = default,
                        accountId = inputArgs[0]
                    ))
                }
            }
        } else {
            // 2 or more inputArgs - must be site and account id or invalid
            val siteArg = inputArgs[0].toLowerCase()
            val site = TargetMatch.values()
                .firstOrNull { supportedSite ->
                    supportedSite.alias.contains(siteArg)
                }

            if(site == null) {
                return@with Err("Unknown/unsupported target **${inputArgs[0]}**.")
            }

            Ok(TargetArguments(site = site, accountId = inputArgs[1]))
        }
    }
}