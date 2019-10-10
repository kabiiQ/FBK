package moe.kabii.discord.command.commands.utility

import discord4j.core.`object`.VoiceState
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer

object BotUtil : CommandContainer {
    object Screenshare : Command("screenshare", "screen-share", "share", "sharescreen", "screanshare") {
        init {
            discord {
                member.voiceState
                    .flatMap(VoiceState::getChannel)
                    .flatMap { channel ->
                        val link = "https://discordapp.com/channels/${target.id.asString()}/${channel.id.asString()}/"
                        embed("[Screenshare channel for **${channel.name}**]($link)")
                    }
                    .switchIfEmpty(embed("You need to be in a voice channel in this guild to use screenshare."))
                    .block()
            }
        }
    }

    object GlitchLink : Command("glitch") {
        init {
            discord {
                val link = "https://discordapp.com/channels/${target.id.asString()}/${chan.id.asString()}/1"
                embed("[;)]($link)").block()
            }
        }
    }
}