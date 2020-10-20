<h1 align="center">
<br> FBK (Fubuki) </br>
</h1>

[![Discord](https://img.shields.io/discord/581785820156002304?label=Discord&logo=discord&logoColor=ffffff&color=7289da&labelColor=6A7EC2)](https://discord.com/invite/ucVhtnh)
![License](https://img.shields.io/github/license/kabiiQ/FBK)
![Commit](https://img.shields.io/github/last-commit/kabiiQ/fbk)

FBK is a publicly-hosted chat bot for your [Discord](https://discord.com/) server. 

FBK is currently in active development and will see a public release in 2020.

# Features
FBK is a general-purpose bot with a focus on **utility commands** and **service integration**. 

We try to cover a wide range of functionality with the bot, so not all features will be described here. 

A raw [**command list**](https://github.com/kabiiQ/FBK/wiki/Command-List) is available. 

**Features include, and are not limited to:**

- [Music Player](https://github.com/kabiiQ/FBK/wiki/Music-Player)
  - [Play songs from multiple locations](https://github.com/kabiiQ/FBK/wiki/Music-Player#playing-audio)
  - [Song queue with vote-skip and force-skip](https://github.com/kabiiQ/FBK/wiki/Music-Player#queue-manipulation)
    - Moderator commands: [*Temporarily interrupt a song already playing, or add to the front of the queue*](https://github.com/kabiiQ/FBK/wiki/Music-Player#playing-audio)
  - [**Fast forward/rewind/skip** to timestamp in songs at will](https://github.com/kabiiQ/FBK/wiki/Music-Player#playback-manipulation)
  - [**Speed up songs**, change their pitch, or apply a bass boost](https://github.com/kabiiQ/FBK/wiki/Music-Player#audio-manipulationfilters)

- Automatic role assignment/removal on (each only if configured):
  - [User joining server (per invite code if needed)](https://github.com/kabiiQ/FBK/wiki/Auto-Roles#assigning-a-role-to-users-joining-your-server)
  - [User joining/leaving voice channels](https://github.com/kabiiQ/FBK/wiki/Auto-Roles#assigning-a-role-to-users-in-a-voice-channel)
  - [User **reactions** on a specific message **(reaction roles)**](https://github.com/kabiiQ/FBK/wiki/Auto-Roles#assigning-a-role-to-users-reacting-to-a-specific-message)
  - [User running a custom command](https://github.com/kabiiQ/FBK/wiki/Command-Roles#custom-role-commands)
  - [Reassigning user roles when they rejoin server](https://github.com/kabiiQ/FBK/wiki/Configuration-Commands#available-options-in-serverconfig)

- Service Integration
  - [Livestream integration](https://github.com/kabiiQ/FBK/wiki/Livestream-Tracker)
    - Post information on specific streams while they are live
    - Optionally mention a role when they become live
    - Currently supports **Twitch** and **YouTube** live streams
  - [Anime/manga list integration](https://github.com/kabiiQ/FBK/wiki/Anime-List-Tracker)
    - Post information when tracked user's lists are updated
    - Currently supports **MyAnimeList and Kitsu**

- General Utility
  - [**Set timed reminders**](https://github.com/kabiiQ/FBK/wiki/Utility-Commands#reminders)
  - [**Starboard**](https://github.com/kabiiQ/FBK/wiki/Starboard)
  - [Teamspeak-style temporary voice channels](https://github.com/kabiiQ/FBK/wiki/Utility-Commands#temporary-voice-channels)
  - [Get user avatars](https://github.com/kabiiQ/FBK/wiki/Discord-Info-Commands#get-user-avatar), [account creation dates](https://github.com/kabiiQ/FBK/wiki/Discord-Info-Commands#user-info-summary-server-join-time)
  - [Access server voice channel screenshare](https://github.com/kabiiQ/FBK/wiki/Discord-Info-Commands#user-info-summary-server-join-time)

- General Configurability
  - [Change bot prefix freely or add a command suffix instead](https://github.com/kabiiQ/FBK/wiki/Configuration#changing-command-prefix-andor-suffix)
  - Targeted functionality such as music bot commands and service integration features [**need to be enabled on a per-channel basis**](https://github.com/kabiiQ/FBK/wiki/Configuration-Commands#the-config-command-channel-features) to avoid unwanted use or abuse by server members]
  - [Blacklist specific bot commands or require commands to be whitelisted](https://github.com/kabiiQ/FBK/wiki/Configuration#using-a-command-blacklist-or-whitelist) if further usage restriction is required


- Overall, FBK is not intended to be focused on server moderation. However, some of her available **moderation utilites** include:
  - [**Configurable, comprehensive moderation logs**](https://github.com/kabiiQ/FBK/wiki/Moderation-Logs)
  - [Purging messages from a chat](https://github.com/kabiiQ/FBK/wiki/Purge-Messages)
  - [Mass-move users between voice channels](https://github.com/kabiiQ/FBK/wiki/Moderation-Commands#mass-drag-users-in-voice-channels)


# Development 
... and more to come! There is plenty planned for when I have time and motivation (university student + PT work + the hustle).

Current feature/issue plans are tracked on my [Glo Board](https://app.gitkraken.com/glo/board/XRmi8OAM1wAPgyBv).

Feature ideas/requests, issue reports, and general questions are welcome in the bot's [Discord server](https://discord.com/invite/ucVhtnh).

This bot is written in [Kotlin](https://kotlinlang.org/).

The big libraries making my work on KizunaAi doable are [Discord4J](https://github.com/Discord4J/Discord4J/) and [LavaPlayer](https://github.com/sedmelluq/lavaplayer/). All dependencies being pulled can be found in the [build](https://github.com/kabiiQ/FBK/blob/master/build.gradle.kts#L42) file.

## Self-Hosting:
As an open-source project, KizunaAi can be compiled using her Gradle build script, and ran independently. To run a custom version of the bot will require editing keys.toml with your own API keys. As provided, she will require access to a [MongoDB](https://www.mongodb.com/try/download/community) and a [PostgreSQL](https://www.postgresql.org/download/) server. Detailed instructions or support should not be expected for this use case. You are free to do so, but I would still appreciate your feedback for the public version or your membership in the community Discord server.


# Licensing / Liability

This Discord bot is named after, but has no association to the virtual YouTuber [Shirakami Fubuki](https://www.youtube.com/channel/UCdn5BQ06XqgXoAxIhbqw5Rg), a streamer with [Hololive](https://www.youtube.com/channel/UCJFZiqLMntJufDCHc6bQixg).

FBK is licensed under the GPL 3.0 license, viewable in the ``LICENSE`` file. 

 Some commands may echo user input. No user-created content should be considered as an opinion or statement from myself. 