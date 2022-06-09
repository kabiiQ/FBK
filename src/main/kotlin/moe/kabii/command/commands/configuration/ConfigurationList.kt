package moe.kabii.command.commands.configuration

import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds

object ConfigurationList : Command("configs") {

    override val wikiPath: String = "https://github.com/kabiiQ/FBK/wiki/Configuration-Commands"

    init {
        chat {
            val configs = StringBuilder()
                .appendLine("***--- Channel Specific Settings ---***")
                .appendLine("`/feature config`: Channel-specific features")
                .appendLine("`/log config`: Enabled mod logs")
                .appendLine("`/animeconfig config`: Anime list tracker settings")
                .appendLine("`/usetracker`: Configure the default site for /track")
                .appendLine("`/streamcfg config`: General livestream tracker settings (including youtube, twitch, etc)")
                .appendLine("`/yt config`: YouTube-specific tracker settings")
                .appendLine("`/streamrenamecfg`: Channel renaming/oshi marks (livestream tracker) settings")
                .appendLine("`/twitter config`: Twitter-specific tracker settings")
                .appendLine()
                .appendLine("**--- Server-wide Settings ---**")
                .appendLine("`/setmention`: Configure roles/text to be pinged when tracked channels go live")
                .appendLine("`/musiccfg config`: Music bot settings")
                .appendLine("`/servercfg config`: Server-wide settings")
                .appendLine("`/starboard config`: Starboard settings")
                .appendLine("`/welcome config`: User \"welcome bot\" settings")
                .appendLine("`/customcommands`: Add/remove custom commands in your server")
            ereply(
                Embeds
                    .fbk(configs.toString())
                    .withTitle("Available Configuration Commands")
            ).awaitSingle()
        }
    }
}