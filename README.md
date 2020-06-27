<h1 align="center">
<br> FBK (Fubuki) </br>
</h1>

## *Badges will go here on release*

FBK is a publicly-hosted chat bot for your [Discord](https://discord.com/) server. 

FBK is currently in active development and will see a public release in 2020.

# Features
FBK is a general-purpose bot with a focus on **utility commands** and **service integration**. 

We try to cover a wide range of functionality with the bot, so not all commands will be listed here. 
**Features include, and are not limited to:**

(link wiki here to all topics when done)
- Music Player
  - Play songs from multiple locations
  - Song queue with vote-skip and force-skip
    - Moderator commands: *Temporarily interrupt a song already playing, or add to the front of the queue*
  - **Fast forward/rewind/skip** to timestamp in songs at will
  - **Speed up songs**, change their pitch, or apply a bass boost

- Automatic role assignment/removal on (each only if configured):
  - User joining server (per invite code if needed)
  - User joining/leaving voice channels
  - User **reactions** on a specific message **(reaction roles)**
  - User running a custom command
  - Reassigning user roles when they rejoin server

- Service Integration
  - Livestream integration
    - Post information on specific streams while they are live
    - Optionally mention a role when they become live
    - Currently supports **Twitch** (eventual plans for Mixer and Youtube streams)
  - Anime/manga list integration
    - Post information when tracked user's lists are updated
    - Currently supports **MyAnimeList and Kitsu**

- General Utility
  - **Set timed reminders**
  - **Teamspeak-style temporary voice channels**
  - Get user avatars, account creation dates
  - Access server voice channel screenshare

- General Configurability
  - Change bot prefix freely or add a command suffix instead
  - Targeted functionality such as music bot commands and service integration features **need to be enabled on a per-channel basis** to avoid unwanted use or abuse by server members
  - Blacklist specific bot commands or require commands to be whitelisted if further usage restriction is required


- Overall, FBK is not intended to be focused on server moderation. However, some of her available **moderation utilites** include:
  - **Configurable, comprehensive moderation logs**
  - Purging messages from a chat
  - Mass-move users between voice channels


# Development 
... and more to come! There is plenty planned for when I have time and motivation (university student + PT work + the hustle).

Current feature/issue plans are tracked on my [Glo Board](https://app.gitkraken.com/glo/board/XRmi8OAM1wAPgyBv).

Feature ideas/requests, issue reports, and general questions are welcome in the bot's [Discord server](discord.com/invite/ucVhtnh).

The big libraries making my work on KizunaAi doable are [Discord4J](https://github.com/Discord4J/Discord4J/) and [LavaPlayer](https://github.com/sedmelluq/lavaplayer/). All dependencies being pulled can be found in the [build](https://github.com/kabiiQ/FBK/blob/master/build.gradle.kts#L37) file.

## Self-Hosting:
As an open-source project, KizunaAi can be compiled using her Gradle build script, and ran independently. To run a custom version of the bot will require editing keys.toml with your own API keys. As provided, she will require access to a MongoDB and a PostgreSQL server. Detailed instructions or support should not be expected for this use case. You are free to do so, but I would still appreciate your feedback for the public version or your membership in the community Discord server.


# Licensing / Liability

This Discord bot is named after, but has no association to the virtual YouTuber [Shirakami Fubuki](https://www.youtube.com/channel/UCdn5BQ06XqgXoAxIhbqw5Rg), a streamer with [Hololive](https://www.youtube.com/channel/UCJFZiqLMntJufDCHc6bQixg).

FBK is licensed under the GPL 3.0 license, viewable in the ``LICENSE`` file. 

 Some commands may echo user input. I am not responsible for this user-created content.