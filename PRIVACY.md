# Privacy Policy

It is not the intention of this bot to compile personally identifiable information from users. However, by using this bot some of your information is stored internally on a private server. This document will summarize what information is collected and how it is used.

By adding this bot (FBK) to a Discord server, you accept and understand the policies outlined in this document.

# Data Collected

## Command Requests

As with any Discord bot, any content provided specifically in a command or interaction with this bot will be analyzed and may be stored for debugging. Storage of preferences is required for any functionality of the bot that is configurable. This includes:

- Per-server configurations/settings
- Any additional information provided directly in a command or conversation to the bot

## Command Contents

The bot can see the contents of any command executed that is registered to it, as well as the content of any message sent via Direct Message or mentions the bot user directly. "Message" contents are not stored by this bot. Command contents may be stored temporarily in logs for debugging and testing, and stored in an internal database only if required for some functionality.

## Discord IDs Collected Automatically

IDs (snowflakes) specific to Discord objects (guilds, users, channels, messages) within your Discord server may be collected at any time. User and channel IDs are linked to Discord guilds, and role IDs may be linked to user IDs if automatic role assignment features are enabled. Usernames and channel names corresponding to these IDs are not stored.

## Other Data Collected if Enabled

If the "service integration" features including and not limited to the Twitter, YouTube, Twitch, MyAnimeList "trackers" are used, additionally information will necessarily be obtained and stored from these social platforms. Any other "trackers" that may be added following a similar design pattern will similarly collect similar neccesary unique data.

- Corresponding Account username or ID required for operation 
- Associated usernames

For streaming platforms:

- Event (video upload, livestream, Tweet) unique IDs
- Event titles, live status, schedule, viewer counts, and any information added in the future which can be displayed in bot messages

Anime/manga "media list" trackers:
- Entire contents of tracked media lists necessary for detection of updates to the list 

Twitter feed trackers:
- Recent Tweet IDs

Along with any other data provided to the bot that is reasonably necessary for the services offered.

## Usage of Collected Information

Information collected is used purely to provide you with the services and functionality offered by the bot. It is not distributed directly to any third-party excepting Discord in the form of messages on that platform.

## Data Storage and Security

Stored data is located on a secured server using commercially-acceptable, secure database softwares PostgreSQL and MongoDB. No other users than myself (kabii) have access to the resources to directly access these databases. Although best efforts are made to protect any personal data, no method of electronic transmission or storage can be absolutely guaranteed to be secure. In the unlikely event of a data breach, and the nature of a Discord bot lacking a real direct notification system, any concern over data security would be reported on the bot Discord server.

## Retainment of Personal Information

As described in this document, very little personal information is collected. Any information that is collected is sought to be stored only as long as needed for the service. However, there is no exact time of data expiration. For example, if a user reminder is set for 1 year in the future, the contents of that reminder will be stored until it is sent or cancelled by the user. 

Discord user IDs are not removed from the database unless requested. To the best of my understanding, I would not consider Discord user IDs to be personally identifable. However, the deletion command specified below is still provided.

Discord channel and server IDs and information linked to these IDs are deleted from the database when the bot is removed from a server. There is still a deletion command provided as specified below.

## Deletion of User or Server Information

As required by Discord, a command has been added to this bot for the deletion of user and server data. It seems less than necessary (most data stored is only Discord IDs - which should not be personally identifiable) but it has been provided in good faith.

#### User

Discord user information can be deleted through a self-service command:
`/datadeletionrequest user` 

This command will perform a deletion of your Discord user ID from the database. This action should not be taken lightly. The deletion of this will cause the erasure of any associated data, including (for examples, but not limited to)

- Reminders you have set
- Social feeds (Twitch, YouTube, Twitter, anime lists, ETC) where you were the **user who /track'ed them**

Furthermore, if you request deletion of your Discord ID from the bot but continue to be a member of a Discord server the bot is in, your ID will likely be re-collected quickly, so using this command is not recommended in this case.

#### Server 

Discord server information can be deleted through a self-service command: `/datadeletionrequest server`

This operation can also be used to fully reset the bot configuration, but **EVERYTHING** will be lost. Note that you should never really need to run this command, as removing the bot from a server will also delete the data associated with that server.

This command can only be performed by the owner of the Discord server.

This command will perform a deletion of your Discord server's ID from the database. This action should not be taken lightly. This will delete all configurations regarding your server. 

This will reset your server's configurations and stored data about your server:
- ALL TRACKED SOCIAL FEEDS
- ALL CONFIGURED PROPERTIES
    - AUTO-ROLE SETUPS (reaction roles, etc)
    - STARBOARD SETUP
    - LOG SETUPS
    - ETC

Similar to user data, if the bot is in the server data is deleted from, the server ID will likely be re-collected if any more commands are run or events occur.

If there is an issue with this option, the bot administrator can be contacted on the bot's support Discord server. 