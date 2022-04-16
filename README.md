<h1 style="text-align: center;">
<br> FBK (Fubuki) </br>
</h1>

### Support the Developer

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/E1E5AF13X)

FBK is a publicly-hosted chat bot for your [Discord](https://discord.com/) server. 

# Add FBK to your Discord server

FBK is now available for invite and public use, I have not done any sort of advertising yet, but feel free to use it or share it.

#### [Invite link granting permissions necessary for ALL bot features](https://discord.com/oauth2/authorize?client_id=314672047718531072&permissions=288681168&scope=applications.commands%20bot)

##### [Invite link granting Administrator (all permissions+view all channels)](https://discord.com/oauth2/authorize?client_id=314672047718531072&permissions=8&scope=applications.commands%20bot). For security, this is not recommended unless you have a small server and do not want to deal with permissions.

##### [Invite link granting basic/minimum permissions](https://discord.com/api/oauth2/authorize?client_id=314672047718531072&permissions=3468352&scope=bot%20applications.commands). This is sufficient for basic commands+music bot functionality. If using any more complex features, (for example: auto-roles, renaming channels for livestreams, or moderation logs) you will need to grant the required permissions to the bot through the Discord role system manually, or the bot **will not function properly.** 

# Suggestion/Support Discord Server

[![Discord](https://discord.com/api/guilds/581785820156002304/widget.png?style=banner2)](https://discord.com/invite/ucVhtnh)

# Features
FBK is a general-purpose bot with a focus on **utility commands** and **service integration**. 

We try to cover a wide range of functionality with the bot, so not all features will be described here. 

A raw [**command list**](https://github.com/kabiiQ/FBK/wiki/Command-List) is available for other features.

**Primary Features Include:**

- ## **Service Integration**
  - [Livestream/Video notifications](https://github.com/kabiiQ/FBK/wiki/Livestream-Tracker)
    - Post information on specific streams while they are live
    - Optionally mention a role when they become live
    - Currently supports **Twitch**, **TwitCasting**, **Twitter Spaces** livestreams, and **YouTube** livestreams/video uploads.
  - [Anime/manga list update notifications](https://github.com/kabiiQ/FBK/wiki/Anime-List-Tracker)
    - Post information when tracked user's lists are updated
    - Currently supports **MyAnimeList, kitsu.io, anilist.co**
  - [Twitter feed update notifications](https://github.com/kabiiQ/FBK/wiki/Twitter-Tracker)
    - Post information when specific users post a Tweet!

- ## **[Music Player](https://github.com/kabiiQ/FBK/wiki/Music-Player)**
  - [Play songs from multiple locations](https://github.com/kabiiQ/FBK/wiki/Music-Player#playing-music-with-the-play-command)
  - [Song queue with vote-skip and force-skip](https://github.com/kabiiQ/FBK/wiki/Music-Player#skipping-tracks-in-queue-with-skip)
    - Moderator commands: [*Temporarily interrupt a song already playing, or add to the front of the queue*](https://github.com/kabiiQ/FBK/wiki/Music-Player#--play-is-the-primary-command-for-adding-music-to-the-queue)
  - [**Fast forward/rewind/skip** to timestamp in songs at will](https://github.com/kabiiQ/FBK/wiki/Music-Player#playback-manipulation)
  - [**Speed up songs**, change their pitch, or apply a bass boost](https://github.com/kabiiQ/FBK/wiki/Music-Player#audio-manipulationfilters)

- ## [**Welcome users** to your server](https://github.com/kabiiQ/FBK/wiki/Welcoming-Users)
- ## [**Translator**](https://github.com/kabiiQ/FBK/wiki/Translator)
- ## [Set timed **reminders**](https://github.com/kabiiQ/FBK/wiki/Reminders)
- ## [**Starboard**](https://github.com/kabiiQ/FBK/wiki/Starboard)

- ## **General Utility**
  - [Teamspeak-style temporary voice channels](https://github.com/kabiiQ/FBK/wiki/Utility-Commands#temporary-voice-channels)
  - [Get user avatars](https://github.com/kabiiQ/FBK/wiki/Discord-Info-Commands#get-user-avatar-with-avatar), [account creation dates](https://github.com/kabiiQ/FBK/wiki/Discord-Info-Commands#user-info-summary-server-join-time-with-who)

- ## Game(s)
  - [Connect 4](https://github.com/kabiiQ/FBK/wiki/Games-(Connect-4))

- ## Automatic role assignment/removal on (each only if configured):
  - [User joining server (per invite code if needed)](https://github.com/kabiiQ/FBK/wiki/Auto-Roles#assigning-a-role-to-users-joining-your-server)
  - [User joining/leaving voice channels](https://github.com/kabiiQ/FBK/wiki/Auto-Roles#assigning-a-role-to-users-in-a-voice-channel)
  - [User **reactions** on a specific message **(reaction roles)**](https://github.com/kabiiQ/FBK/wiki/Auto-Roles#assigning-a-role-to-users-reacting-to-a-specific-message)
  - [Reassigning user roles when they rejoin server](https://github.com/kabiiQ/FBK/wiki/Configuration-Commands#available-options-in-serverconfig)

- ## General Configurability
  - Targeted functionality such as music bot commands and service integration features [**need to be enabled on a per-channel basis**](https://github.com/kabiiQ/FBK/wiki/Configuration-Commands#the-config-command-channel-features) to avoid unwanted use or abuse by server members]
  - [Blacklist specific bot commands or require commands to be whitelisted](https://github.com/kabiiQ/FBK/wiki/Configuration#using-a-command-blacklist-or-whitelist) if further usage restriction is required


- Overall, FBK is not intended to be focused on server moderation. However, some of her available **moderation utilites** include:
  - [**Configurable moderation logs (WIP)**](https://github.com/kabiiQ/FBK/wiki/Moderation-Logs)
  - [Purging messages from a chat](https://github.com/kabiiQ/FBK/wiki/Purge-Messages)
  - [Mass-move users between voice channels](https://github.com/kabiiQ/FBK/wiki/Moderation-Commands#mass-drag-users-in-voice-channels-with-drag)


# Development 
![Kotlin](https://img.shields.io/badge/Kotlin-1.6.20-blue.svg?logo=Kotlin)
![Commit](https://img.shields.io/github/last-commit/kabiiQ/fbk)


... and more to come! There is plenty planned for when I have time and motivation (university student + work + the hustle).

Current feature/issue plans are tracked on my [Glo Board](https://app.gitkraken.com/glo/board/XRmi8OAM1wAPgyBv).

Feature ideas/requests, issue reports, and general questions are welcome in the bot's [Discord server](https://discord.com/invite/ucVhtnh).

This bot is written in [Kotlin](https://kotlinlang.org/).

The big libraries making my work on FBK doable are [Discord4J](https://github.com/Discord4J/Discord4J/) and [LavaPlayer](https://github.com/sedmelluq/lavaplayer/). All dependencies being pulled can be found in the [build](https://github.com/kabiiQ/FBK/blob/master/build.gradle.kts#L42) file.

# Licensing / Liability

![License](https://img.shields.io/github/license/kabiiQ/FBK)

This Discord bot is named after, but has no association to the virtual YouTuber [Shirakami Fubuki](https://www.youtube.com/channel/UCdn5BQ06XqgXoAxIhbqw5Rg), a streamer with [Hololive](https://www.youtube.com/channel/UCJFZiqLMntJufDCHc6bQixg).

FBK is licensed under the GPL 3.0 license, viewable in the [LICENSE](https://github.com/kabiiQ/FBK/blob/master/LICENSE) file. 

 Some commands may echo user input. No user-created content should be considered as an opinion or statement from mysel
 
 ## [Privacy Policy](https://github.com/kabiiQ/FBK/blob/master/PRIVACY.md)
 ## [Terms of Service](https://github.com/kabiiQ/FBK/blob/master/TERMS.md)