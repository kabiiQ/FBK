<h1 style="text-align: center;">
<br> FBK (Fubuki) </br>
</h1>

### Support the Developer

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/E1E5AF13X)

FBK is a publicly-hosted chat bot for your [Discord](https://discord.com/) server.

# Add FBK to your Discord server

FBK is now available for invite and public use. Feel free to use it or share it though I don't plan on putting it on any kind of bot list or advertising.

#### [Invite link granting permissions necessary for ALL bot features](https://discord.com/oauth2/authorize?client_id=1113221032908693534&permissions=17875674262608&scope=applications.commands%20bot)

##### [Invite link granting Administrator (all permissions+view all channels)](https://discord.com/oauth2/authorize?client_id=1113221032908693534&permissions=8&scope=applications.commands%20bot). For security, this is not recommended unless you have a small server and do not want to deal with permissions. However, in my experience many servers have many permissions set to "denied" and give their staff Administrator - you may just want to use this role.

# Suggestion/Support Discord Server

[![Discord](https://discord.com/api/guilds/581785820156002304/widget.png?style=banner2)](https://discord.com/invite/ucVhtnh)

# Features
FBK is a general-purpose bot with a focus on **utility commands** and **service integration**.

FBK covers a pretty wide range of functionality, so not all features are necessarily featured here.
A raw [**command list**](https://github.com/kabiiQ/FBK/wiki/Command-List) is available for all features.

**Primary Features Include:**

- ## **Service Integration**
  - [Livestream/Video notifications](https://github.com/kabiiQ/FBK/wiki/Livestream-Tracker)
    - Post information on specific streams while they are live
    - Optionally mention a role when they become live
    - Currently supports **Twitch**, **TwitCasting** livestreams, and **YouTube** livestreams/video uploads.
  - [Anime/manga list update notifications](https://github.com/kabiiQ/FBK/wiki/Anime-List-Tracker)
    - Post information when tracked user's lists are updated
    - Currently supports **MyAnimeList, kitsu.io, anilist.co**
  - [Social media feed update notifications](https://github.com/kabiiQ/FBK/wiki/Social-Media-Tracker)
    - Post information when specific users post a Tweet!
    - Available feeds for tracking are highly limited in 2024, as Twitter has made it very difficult to access their data.
    - Currently supports **Twitter** (limited) and **Bluesky** (experimental)

- ## [**Welcome users** to your server](https://github.com/kabiiQ/FBK/wiki/Welcoming-Users)
- ## [**Translator**](https://github.com/kabiiQ/FBK/wiki/Translator)
- ## [Set timed **reminders**](https://github.com/kabiiQ/FBK/wiki/Reminders)

- ## **General Utility**
  - [Teamspeak-style temporary voice channels](https://github.com/kabiiQ/FBK/wiki/Utility-Commands#temporary-voice-channels)
  - [Get user avatars](https://github.com/kabiiQ/FBK/wiki/Discord-Info-Commands#get-user-avatar-with-avatar), [account creation dates](https://github.com/kabiiQ/FBK/wiki/Discord-Info-Commands#user-info-summary-server-join-time-with-who)

- ## Games
  - [Connect 4](https://github.com/kabiiQ/FBK/wiki/Games#connect-4)
  - [Rock Paper Scissors](https://github.com/kabiiQ/FBK/wiki/Games#rock-paper-scissors)

- ## Automatic role assignment/removal on (each only if configured):
  - [User joining server (per invite code if needed)](https://github.com/kabiiQ/FBK/wiki/Auto-Roles#assigning-a-role-to-users-joining-your-server)
  - [User joining/leaving voice channels](https://github.com/kabiiQ/FBK/wiki/Auto-Roles#assigning-a-role-to-users-in-a-voice-channel)
  - [Users interacting with a **button**](https://github.com/kabiiQ/FBK/wiki/Auto-Roles#assigning-a-role-automatically-using-buttons)
  - [User **reactions** on a specific message **(reaction roles)**](https://github.com/kabiiQ/FBK/wiki/Auto-Roles#assigning-a-role-to-users-reacting-to-a-specific-message)
  - [Reassigning user roles when they rejoin server](https://github.com/kabiiQ/FBK/wiki/Configuration-Commands#available-options-in-serverconfig)

- ## General Configurability
  - Targeted functionality such as service integration features [**need to be enabled on a per-channel basis**](https://github.com/kabiiQ/FBK/wiki/Configuration-Commands#the-config-command-channel-features) to avoid unwanted use or abuse by server members]


- Overall, FBK is not intended to be focused on server moderation. However, some of her available **moderation utilites** include:
  - [Basic moderation logs (WIP)](https://github.com/kabiiQ/FBK/wiki/Moderation-Logs)
  - [Purging messages from a chat](https://github.com/kabiiQ/FBK/wiki/Purge-Messages)
  - [Mass-move users between voice channels](https://github.com/kabiiQ/FBK/wiki/Moderation-Commands#mass-drag-users-in-voice-channels-with-drag)


# Development
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg?logo=Kotlin)
![Commit](https://img.shields.io/github/last-commit/kabiiQ/fbk)


... and more to come! There is plenty planned for when I have time and motivation (university student + work + the hustle).

Current feature/issue plans are tracked on [Trello](https://trello.com/b/S1bfvZi4/fbk).

Feature ideas/requests, issue reports, and general questions are welcome in the bot's [Discord server](https://discord.com/invite/ucVhtnh).

This bot is written in [Kotlin](https://kotlinlang.org/) using the [Discord4J](https://github.com/Discord4J/Discord4J/) library for interaction with Discord.

# Self-Hosting

FBK has been converted to [Docker](https://www.docker.com/) as of FBK version 2.2. As a result, it is now much easier to run the bot on your own PC/server.

This is useful if you want to contribute/debug, or just run your own private instance ("self-hosting"), but will not be fully supported.

The process for self-hosting is now documented on the wiki page here: **[Self-Hosting](https://github.com/kabiiQ/FBK/wiki/Self-Hosting)**

# Licensing / Liability

![License](https://img.shields.io/github/license/kabiiQ/FBK)

This Discord bot is named after, but has no association to the virtual YouTuber [Shirakami Fubuki](https://www.youtube.com/channel/UCdn5BQ06XqgXoAxIhbqw5Rg), a streamer with [Hololive](https://hololive.hololivepro.com/en).

FBK is licensed under the GPL 3.0 license, viewable in the [LICENSE](https://github.com/kabiiQ/FBK/blob/master/LICENSE) file.

Some commands may echo user input. No user-created content should be considered as an opinion or statement from myself.

## [Privacy Policy](https://github.com/kabiiQ/FBK/blob/master/PRIVACY.md)
## [Terms of Service](https://github.com/kabiiQ/FBK/blob/master/TERMS.md)