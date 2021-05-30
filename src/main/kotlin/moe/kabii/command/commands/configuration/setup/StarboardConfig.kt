package moe.kabii.command.commands.configuration.setup

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.rest.util.Image
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.StarboardSetup
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.util.DiscordEmoji
import moe.kabii.util.EmojiUtil
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.orNull
import kotlin.reflect.KMutableProperty1

object StarboardConfig : Command("starboard", "starboardsetup", "setupstarboard", "starboardconfig", "starboargcfg", "starbored", "starredboard", "starbord", "star", "sb") {
    override val wikiPath = "Starboard#starboard-configuration-starboard"

    object StarboardModule : ConfigurationModule<StarboardSetup>(
        "starboard",
        ViewElement("Starboard channel ID",
            listOf("channel", "channelID", "ID"),
            StarboardSetup::channel,
            redirection = "To set the starboard channel, use the **starboard set** command in the desired channel."
        ),
        LongElement("Stars required for a message to be put on the starboard",
            listOf("stars", "min", "starsToAdd", "starsAdd", "minstars", "addstars", "minimumstars"),
            StarboardSetup::starsAdd,
            range = 1..100_000L,
            prompt = "Enter a new value for the number of star reactions required for a message to be put on the starboard."
        ),
        BooleanElement("Remove a message from the starboard if the star reactions are cleared by a moderator",
            listOf("removeOnClear", "removecleared", "removewhencleared", "reactionclear"),
            StarboardSetup::removeOnClear
        ),
        BooleanElement("Remove a message from the starboard if the original message is deleted",
            listOf("removeOnDelete", "removeIfDeleted", "removeIfDelete", "deleteremove"),
            StarboardSetup::removeOnDelete
        ),
        BooleanElement("Mention a user when their message is placed on the starboard",
            listOf("mention", "usermention", "mentionuser", "user", "@"),
            StarboardSetup::mentionUser
        ),
        BooleanElement("Allow messages in NSFW-flagged channels to be starboarded",
            listOf("nsfw", "includensfw", "nsfwinclude"),
            StarboardSetup::includeNsfw
        ),
        CustomElement("Emoji used to add messages to the starboard",
            listOf("emoji", "emote"),
            StarboardSetup::emoji as KMutableProperty1<StarboardSetup, Any?>,
            prompt = "Select an emote that users can add to messages to vote them onto the starboard. For custom emotes, I **MUST** be in the server the emote is from or things will not function as intended. Enter **reset** to restore the default star: ${EmojiCharacters.star}",
            default = null,
            parser = ::setStarboardEmoji,
            value = { starboard -> starboard.useEmoji().string() }
        )
    )

    init {
        discord {
            member.verify(Permission.MANAGE_CHANNELS)
            val action = when(args.getOrNull(0)?.lowercase()) {
                "here", "set", "create", "move", "enable", "setup", "on", "1" -> ::createStarboard
                "disable", "unset", "remove", "delete", "off", "0" -> ::disableStarboard
                else -> ::configStarboard // if not set/disable, run standard configurator
            }
            action(this)
        }
    }

    private suspend fun createStarboard(origin: DiscordParameters) = with(origin) {
        // get channel target (starboard create #channel)
        val channelArg = args.getOrNull(1)
        val channelTarget = if(channelArg != null) {
            val search = Search.channelByID<GuildChannel>(this, channelArg)
            if(search == null) {
                error("Unable to find channel **$channelArg**.").awaitSingle()
                return@with
            } else search
        } else guildChan

        // check if guild has starboard
        val current = config.starboard
        if(current != null) {
            if(current.channel == channelTarget.id.asLong()) {
                error {
                    setAuthor(target.name, null, target.getIconUrl(Image.Format.PNG).orNull())
                    setDescription("Starboard already enabled in ${channelTarget.mention}.")
                }.awaitSingle()
                return@with
            } else {
                // move starboard to new channel
                current.channel = channelTarget.id.asLong()
                config.save()
                embed {
                    setAuthor(target.name, null, target.getIconUrl(Image.Format.PNG).orNull())
                    setDescription("Starboard has been moved to ${channelTarget.mention}}.")
                }.awaitSingle()
            }
        } else {
            // create new starboard config
            val new = StarboardSetup(channelTarget.id.asLong())
            config.starboard = new
            config.save()
            embed {
                setAuthor(target.name, null, target.getIconUrl(Image.Format.PNG).orNull())
                setDescription("Starboard has been created. Messages which receive ${new.starsAdd} stars ${EmojiCharacters.star} will be placed on the starboard in ${channelTarget.mention}. This threshold can be changed by running the [**starboard stars <star requirement>**](https://github.com/kabiiQ/FBK/wiki/Starboard#starboard-configuration-starboard) command.")
            }.awaitSingle()
        }
    }

    private suspend fun disableStarboard(origin: DiscordParameters) = with(origin) {
        val current = config.starboard
        if(current == null) {
            error("**${target.name}** does not currently have a starboard.").awaitSingle()
            return@with
        }

        config.starboard = null
        config.save()
        embed {
            setAuthor(target.name, null, target.getIconUrl(Image.Format.PNG).orNull())
            setDescription("Starboard has been disabled.")
        }.awaitSingle()
    }

    private suspend fun configStarboard(origin: DiscordParameters) = with(origin) {
        if(config.starboard == null) {
            error("There is no currently no starboard ${EmojiCharacters.star} for **${target.name}**. Run the **starboard set** command in a channel to make it into your server's starboard.").awaitSingle()
            return
        }
        val configurator = Configurator(
            "Starboard settings for ${target.name}",
            StarboardModule,
            checkNotNull(config.starboard)
        )

        if(configurator.run(this)) {
            config.save()
        }
    }

    private val resetEmoji = Regex("(reset|star)", RegexOption.IGNORE_CASE)
    private suspend fun setStarboardEmoji(origin: DiscordParameters, message: Message, value: String): Result<DiscordEmoji?, Unit> {
        if(resetEmoji.matches(value)) return Ok(null)
        val emoji = EmojiUtil.parseEmoji(value)
        return if(emoji == null) {
            origin.error("$value is not a usable emoji.").awaitSingle()
            Err(Unit)
        } else Ok(emoji)
    }
}