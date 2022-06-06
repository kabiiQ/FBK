# ℹ Command List

This page is an automatically generated list of all bot commands with a link to their wiki page for further information.

> 🔍 Commands labeled as "User Command" are accessible in Discord by the context or "right-click" menu on a user. 
> 🔍 Commands labeled as "Message Command" are found in the context menu on messages.

> ❓ For all command options, `*` indicates it is required to run the command. All other options are "optional" and are not required in the Discord client, but may be useful to change the behavior of some commands.

### - `/music`:

- (Music bot) Apply a "filter" to the currently playing audio.
- Wiki: [[Music-Player#audio-manipulationfilters]]

#### -- `/music volume`

- Change the playback volume.

| Option | Type | Description
| ---    | ---  | ---
| `percent` | Integer | The volume level. Default volume is 15.
#### -- `/music sample`

- Play the current track for a specified amount of time and then automatically skip it.

| Option | Type | Description
| ---    | ---  | ---
| `duration*` | String | Duration of the track to sample. For example, 'sample 2m' will play 2 more minutes of audio.
#### -- `/music sampleto`

- Similar to sample, instead sampleto only plays the current track until a specific timestamp.

| Option | Type | Description
| ---    | ---  | ---
| `timestamp*` | String | Timestamp at which to skip. For example, 'sampleto 2m' will skip the current track at 2:00 in.
#### -- `/music speed`

- Manipulate the playback speed.

| Option | Type | Description
| ---    | ---  | ---
| `percent*` | Integer | % to manipulate the audio playback speed. "100" or using /reset will restore normal speed.
#### -- `/music pitch`

- Manipulate the playback pitch.

| Option | Type | Description
| ---    | ---  | ---
| `percent*` | Integer | % to manipulate the audio pitch. "100" or using /reset will return the pitch to normal.
#### -- `/music bass`

- Apply a bass boost.

| Option | Type | Description
| ---    | ---  | ---
| `boost` | Integer | % of maximum bass boost to apply (0-100). "0" or using /reset will remove the boost.
#### -- `/music rotate`

- Apply a 3D audio effect where the audio rotates 'around' the listener.

| Option | Type | Description
| ---    | ---  | ---
| `speed` | Decimal | The speed of the rotation effect. The default is .25
#### -- `/music doubletime`

- Applies a "double time" filter equivalent to: speed 125

#### -- `/music nightcore`

- Applies a "nightcore" filter equivalent to: speed 125 + pitch 125

#### -- `/music daycore`

- Applies a "daycore" filter equivalent to: speed 75 + pitch 75

#### -- `/music reset`

- Resets all active audio filters to return audio to normal.



### - `/np`:

- (Music bot) Displays info on the audio that is "now playing" (np).
- Wiki: [[Music-Player#--music-queue-information]]



### - `/play`:

- (Music bot) Add a song or audio clip to the queue.
- Wiki: [[Music-Player#Music-Player#playing-music-with-the-play-command]]

| Option | Type | Description
| ---    | ---  | ---
| `song*` | String | Provide either: a YouTube video ID, a YouTube search query, or a direct link to a supported source.
| `playlist` | True/False | Set to True to add ALL tracks from a YouTube playlist to the queue.
| `forceplay` | True/False | Play this track immediately, pausing any current track until the new one ends.
| `next` | True/False | Add this track to the front of the queue rather than the end ("skipping the line")
| `attachment` | Attachment | Optionally play an attached audio file directly. The 'song' text will be ignored.
| `volume` | Integer | Set the volume level for this track (for example, if this track is known to be very quiet)


### - `/queue`:

- (Music bot) View and edit the music queue.
- Wiki: [[Music-Player#--music-queue-information]]

#### -- `/queue list`

- View the current music queue.

| Option | Type | Description
| ---    | ---  | ---
| `from` | Integer | Optionally specify the track to start listing the queue from, such as queue 10.
#### -- `/queue remove`

- Remove music from the queue.

#### -- `/queue remove tracks`

- Remove tracks by their position in the queue.

| Option | Type | Description
| ---    | ---  | ---
| `numbers*` | String | Track # in queue to remove. Accepts ranges such as: remove 1, remove 1-4, remove 3-, remove all
#### -- `/queue remove user`

- Remove tracks by the user who queued them.

| Option | Type | Description
| ---    | ---  | ---
| `who*` | User | The user to remove queued tracks from.
#### -- `/queue pause`

- Pause audio playback indefinitely.

#### -- `/queue resume`

- Resume audio playback if paused.

#### -- `/queue loop`

- Toggle queue looping. When enabled, tracks are re-added to the queue after they finish playing.

#### -- `/queue replay`

- Re-add the currently playing audio track to the end of the queue.

#### -- `/queue shuffle`

- Shuffles the audio tracks currently in queue.

#### -- `/queue clear`

- Removes all audio tracks waiting in queue. Does not skip the current track.



### - `/search`:

- (Music bot) Searches for a track by name, allowing you to select the correct track to play.
- Wiki: [[Music-Player#playing-music-with-the-play-command]]

| Option | Type | Description
| ---    | ---  | ---
| `search*` | String | The text to search for
| `site` | Integer | The site to search on. If not provided, default to YouTube


### - `/seek`:

- (Music bot) Skip around in the currently playing audio.
- Wiki: [[Music-Player#playing-music-with-the-play-command]]

#### -- `/seek time`

- Seek to a specific timestamp in this track.

| Option | Type | Description
| ---    | ---  | ---
| `timestamp*` | String | Timestamp to skip playback to in this track. Example, /seek time 2:10
#### -- `/seek forward`

- Skip forward in this track. (Fast-forward)

| Option | Type | Description
| ---    | ---  | ---
| `time` | String | Time to skip forwards in the current track. If no time is provided, 30 seconds will be used.
#### -- `/seek backward`

- Skip backward in this track. (Rewind)

| Option | Type | Description
| ---    | ---  | ---
| `time` | String | Time to skip backwards in the current track. If no time is provided, 30 seconds will be used.


### - `/skip`:

- (Music bot) Vote to skip the currently playing audio. Will instantly skip if you have permission.
- Wiki: [[Music-Player#queue-manipulation]]



### - `/stop`:

- (Music bot) Combines 'clear' and 'skip'. Skips all tracks in queue (for which you have permission).
- Wiki: [[Music-Player#queue-manipulation]]



### - `/summon`:

- (Music bot) Summon me into your voice channel. (This is done automatically when playing music.)
- Wiki: [[Music-Player#Music-Player#commands]]



### - `/autorole`:

- Configure automatic role assignment rules.
- Wiki: [[Auto-Roles]]

#### -- `/autorole join`

- Configures an auto-role for user "joins"

#### -- `/autorole join create`

- Create a join-based autorole

| Option | Type | Description
| ---    | ---  | ---
| `role*` | Role | Role to be added to users who join your server
| `invite` | String | If provided, this autorole will only apply to a specific invite code
#### -- `/autorole join delete`

- Delete an existing join-based autorole rule. By default deletes the rule for "all" channels

| Option | Type | Description
| ---    | ---  | ---
| `role*` | Role | Role associated with the autorole rule to be deleted
| `invite` | String | Invite code associated with the autorole rule to be deleted (optional)
#### -- `/autorole join list`

- List all join-based autoroles

#### -- `/autorole voice`

- Configure an autorole for users joining voice channels

#### -- `/autorole voice create`

- Create a voice channel-based autorole

| Option | Type | Description
| ---    | ---  | ---
| `channel` | Channel | If provided, this autorole will only be applied to users joining this SPECIFIC voice channel.
#### -- `/autorole voice delete`

- Delete an existing voice-based autorole rule

| Option | Type | Description
| ---    | ---  | ---
| `channel` | Channel | Delete the auto-role for a specific voice channel.
#### -- `/autorole voice list`

- List all voice-based autorole rules.

#### -- `/autorole reaction`

- Configure an auto-role for users reacting on a message

#### -- `/autorole reaction create`

- Create a reaction-based autorole

| Option | Type | Description
| ---    | ---  | ---
| `message*` | String | ID of the Discord message to add the reaction role to
| `emoji*` | String | The emoji to be used for the reaction role
| `role*` | Role | The role to be added when users react with this emoji
#### -- `/autorole reaction delete`

- Delete an existing reaction-based autorole rule

| Option | Type | Description
| ---    | ---  | ---
| `message*` | String | The ID of the Discord message to remove reaction roles from.
| `emoji` | String | The emoji of the reactionrole rule to remove, otherwise removes ALL reactionroles on this message.
#### -- `/autorole reaction list`

- List all reaction-based autorole rules (reactionroles).

#### -- `/autorole reaction reset`

- Manually reset the reaction counts for reaction roles in this channel back to 0

#### -- `/autorole button`

- Configure auto-roles where users can press a button to receive a role.

#### -- `/autorole button create`

- Create an auto-role where users can press a button to receive a role.

| Option | Type | Description
| ---    | ---  | ---
| `style*` | Integer | Select to use separate buttons for each role or a drop-down list to select roles.
| `message` | String | A message to be written above the auto-role buttons. Can be edited later. Use 
 for new lines.
#### -- `/autorole button edit`

- Edit an existing button-based auto-role message.

| Option | Type | Description
| ---    | ---  | ---
| `id*` | String | The message ID of the button-based auto-roles to be edited.
| `style` | Integer | Include to edit the style: separate buttons for each role or a drop-down list to select roles.
| `message` | String | Include to edit the message written above the auto-role buttons. Use 
 for new lines.
| `maxroles` | Integer | For role LISTS only. Limit the roles a user can select, such as for color roles. 0=Unlimited
| `listroles` | True/False | Set to False to disable the list of roles in the button-role message.
#### -- `/autorole button addrole`

- Add a role to an existing button-based auto-role message.

| Option | Type | Description
| ---    | ---  | ---
| `id*` | String | The message ID of the button-based auto-roles to be edited.
| `role*` | Role | The role that users will be assigned.
| `info` | String | Information about this role that will be presented to users.
| `emoji` | String | An emoji that will represent this role on buttons.
| `name` | String | An alternate name to use for this role on buttons/lists. Otherwise, the role name will be used.
#### -- `/autorole button removerole`

- Remove a role from an existing button-based auto-role message.

| Option | Type | Description
| ---    | ---  | ---
| `id*` | String | The message ID of the button-based auto-roles to be edited.
| `role*` | String | The role ID to be removed. Use /autorole button to remove a button setup entirely.
#### -- `/autorole button delete`

- DELETE a button-based auto-role message. Use /autorole button removerole to remove a single role.

| Option | Type | Description
| ---    | ---  | ---
| `id*` | String | The message ID of the button-based auto-roles to be edited.
#### -- `/autorole button convert`

- Convert existing FBK reaction-roles on a message into button-roles

| Option | Type | Description
| ---    | ---  | ---
| `message*` | String | The Discord ID of the message containing FBK reaction-roles to be converted into button-roles.
| `style*` | Integer | Include to edit the style: separate buttons for each role or a drop-down list to select roles.


### - `/channels`:

- List channels in this server with enabled features.
- Wiki: [[Configuration-Commands#the-feature-command-channel-features]]



### - `/customcommand`:

- Add or remove basic custom commands
- Wiki: [[Custom-Commands]]

#### -- `/customcommand add`

- Add or update a custom command.

| Option | Type | Description
| ---    | ---  | ---
| `command*` | String | The name of the new custom command.
| `response*` | String | The response that will be sent when this command is used.
| `description` | String | The description for this command in Discord.
| `private` | True/False | If True, this command will be sent as a message that only the user running it can view.
#### -- `/customcommand remove`

- Remove an existing custom command.

| Option | Type | Description
| ---    | ---  | ---
| `command*` | String | The name of the custom command to be removed.
#### -- `/customcommand list`

- List existing custom commands



### - `/roleset`:

- Create or modify a mutually exclusive set of roles

#### -- `/roleset create`

- Creates a mutually exclusive set of roles

| Option | Type | Description
| ---    | ---  | ---
| `name*` | String | The name of the new role set being created
#### -- `/roleset delete`

- Remove an existing mutually exclusive set of roles

| Option | Type | Description
| ---    | ---  | ---
| `name*` | String | The name of the existing role set to be removed
#### -- `/roleset add`

- Add a role to an existing exclusive role set

| Option | Type | Description
| ---    | ---  | ---
| `name*` | String | The name of the existing role set to add the role to
| `role*` | Role | The role to add to this mutually exclusive role set
#### -- `/roleset remove`

- Remove a role currently part of an exclusive role set

| Option | Type | Description
| ---    | ---  | ---
| `name*` | String | The name of the existing role set to remove a role from
| `role*` | Role | The role to remove from this mutually exclusive role set
#### -- `/roleset list`

- List existing sets of mutually exclusive roles.



### - `/connect4`:

- Connect 4 has moved! Use /game connect4



### - `/game`:

- Challenge a friend to a game within Discord!
- Wiki: [[Games]]

#### -- `/game connect4`

- Challenge a friend to a game of Connect 4!

| Option | Type | Description
| ---    | ---  | ---
| `user*` | User | The user to challenge.
#### -- `/game rps`

- Challenge a friend to a game of Rock Paper Scissors.

| Option | Type | Description
| ---    | ---  | ---
| `user*` | User | The user to challenge.
| `rounds` | Integer | Change how many rounds are played! Defaults to best of 3.
#### -- `/game tictactoe`

- Challenge a friend to a game of Tic-tac-toe.

| Option | Type | Description
| ---    | ---  | ---
| `user*` | User | The user to challenge.


### - `/botinfo`:

- Display basic bot information: uptime, version
- Wiki: [[Bot-Meta-Commands#bot-info-command]]



### - `/datadeletionrequest`:

- (Privacy) Delete any collected data on your Discord user account or server.

#### -- `/datadeletionrequest user`

- Delete ALL INFORMATION concerning your USER ACCOUNT. Irreversible, breaking operation.

#### -- `/datadeletionrequest server`

- Delete ALL INFORMATION concerning this Discord server. Irreversible, breaking operation.



### - `/help`:

- Display information about FBK's commands
- Wiki: [[Bot-Meta-Commands#command-information]]

#### -- `/help command`

- Display information on a specific command

| Option | Type | Description
| ---    | ---  | ---
| `command*` | String | The command to look up.
#### -- `/help wiki`

- Display a link to the general FBK wiki page



### - `/ping`:

- Test FBK's ability to respond to you on Discord.
- Wiki: [[Bot-Meta-Commands#ping]]



### - `/purge`:

- Purge messages from this channel.
- Wiki: [[Purge-Messages]]

#### -- `/purge count`

- Purge a specific number of messages.

| Option | Type | Description
| ---    | ---  | ---
| `number*` | Integer | The number of messages to purge.
#### -- `/purge from`

- Purge message starting at a specific message ID.

| Option | Type | Description
| ---    | ---  | ---
| `start*` | String | Message ID to start the purge from.
| `end` | String | Message ID to end the purge at, otherwise it will continue until the most recent messages.


### - `/randomizecolor`:

- Selects a randomized color for a role.
- Wiki: [[Moderation-Commands#randomizing-a-roles-color-with-randomizecolor]]

| Option | Type | Description
| ---    | ---  | ---
| `role*` | Role | The role to change the color of.


### - `/ask`:

- Ask the bot a question (get a random Magic 8 Ball response)
- Wiki: [[RNG-Commands#the-ask-command]]

| Option | Type | Description
| ---    | ---  | ---
| `question` | String | The "question".


### - `/coinflip`:

- Flip a coin!
- Wiki: [[RNG-Commands#the-coinflip-command]]



### - `/emojify`:

- Convert some text to regional indicator emojis.
- Wiki: [[RNG-Commands#text-to-regional-indicator-emoji-]]

| Option | Type | Description
| ---    | ---  | ---
| `text*` | String | The text to convert to emojis.


### - `/garble`:

- Garble a text message like you can't consistently type.
- Wiki: [[RNG-Commands#garble-text-]]

| Option | Type | Description
| ---    | ---  | ---
| `text*` | String | The text to garble.


### - `/pick`:

- Pick an option from a list.
- Wiki: [[RNG-Commands#the-pick-command]]

| Option | Type | Description
| ---    | ---  | ---
| `list` | String | Space-separated list of "options" to pick randomly from.


### - `/roll`:

- Roll a random number.
- Wiki: [[RNG-Commands#the-roll-command]]

#### -- `/roll range`

- Roll between two numbers.

| Option | Type | Description
| ---    | ---  | ---
| `from` | Integer | The lowest possible roll. Default: 0
| `to` | Integer | The highest possible roll. Default: 100
#### -- `/roll dice`

- Roll some dice.

| Option | Type | Description
| ---    | ---  | ---
| `sides` | Integer | How many 'sides' are on the rolled dice. Default: 6
| `count` | Integer | How many 'dice' to roll. Default: 1


### - `/remind`:

- Create a reminder to be sent to you in the near future.
- Wiki: [[Reminders]]

| Option | Type | Description
| ---    | ---  | ---
| `time*` | String | The time until this reminder should be sent. Examples: 2m or 6h or 1w
| `message` | String | What you would like to be reminded about.
| `dm` | True/False | If you would like this reminder sent via DM rather than in this channel.


### - `/remindcancel`:

- Cancel an existing reminder that is no longer needed.
- Wiki: [[Reminders]]

| Option | Type | Description
| ---    | ---  | ---
| `reminder*` | Integer | The ID of the reminder you would like to cancel, found where you created the reminder.


### - `/calc`:

- Perform a calculation using the WolframAlpha knowledge engine.
- Wiki: [[Lookup-Commands#wolframalpha-queries-calc]]

| Option | Type | Description
| ---    | ---  | ---
| `query*` | String | The calculation to perform.


### - `/pixiv`:

- A very rudimentary Pixiv image embedder.

| Option | Type | Description
| ---    | ---  | ---
| `url*` | String | The Pixiv URL to get images from.
| `start` | Integer | The first image in the Pixiv album to include. Defaults to 1.
| `count` | Integer | How many images to include. Default behavior will include up to 6 images after StartImage.


### - `/skeb`:

- Search skeb.jp for a user's profile.
- Wiki: [[Lookup-Commands#skeb-profile-lookup-skeb]]

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The skeb username to look up


### - `/ttv`:

- Look up current information on a Twitch livestream.
- Wiki: [[Lookup-Commands#twitch-stream-lookup-ttv]]

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The Twitch username to look up.


### - `/twittervid`:

- Gets a playable video from a Tweet.

| Option | Type | Description
| ---    | ---  | ---
| `url*` | String | The Tweet URL to check for a video.


### - `/ud`:

- Look up a term on Urban Dictionary.
- Wiki: [[Lookup-Commands#urbandictionary-lookup-ud]]

| Option | Type | Description
| ---    | ---  | ---
| `term*` | String | The term to look up on Urban Dictionary.


### - `/xkcd`:

- Look up a comic from xkcd.
- Wiki: [[Lookup-Commands#xkcd-comics-xkcd]]

| Option | Type | Description
| ---    | ---  | ---
| `id` | Integer | The ID of the xkcd comic to retrieve. If not provided, returns the current comic.


### - `/setmention`:

- Configure a role to be mentioned when a tracked channel goes live.
- Wiki: [[Livestream-Tracker#-pinging-a-role-with-setmention]]

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The tracked stream/twitter feed that should send a ping
| `role` | Role | The role that should be pinged. If empty, any configured role will no longer be pinged.
| `text` | String | Text to be included along with the ping. If empty, any existing text will be removed.
| `site` | Integer | The site name may need to be specified if it can not be inferred.


### - `/streamrenamecfg`:

- Configure the 'channel rename' stream tracker feature.
- Wiki: [[Livestream-Tracker#setting-stream-specific-charactersemoji]]

#### -- `/streamrenamecfg set`

- Set a character to represent a specific stream. See wiki for details

| Option | Type | Description
| ---    | ---  | ---
| `stream*` | String | The username/id of the tracked stream being configured.
| `character` | String | The character/oshi mark for this channel. Will be removed if not specified
| `site` | Integer | The site name may need to be specified if it can not be inferred.
#### -- `/streamrenamecfg list`

- List the existing configured characters/marks.



### - `/track`:

- Track a streamer's channel (yt, twitch, twitcasting) or Twitter feed. See the wiki for more details.
- Wiki: [[Livestream-Tracker]]

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The username to track. @username for Twitter, username for Twitch, channel ID for YouTube (see wiki)
| `site` | Integer | The site name may need to be specified if it can not be inferred.


### - `/tracked`:

- List targets that are currently tracked in this channel.



### - `/trackvid`:

- Track a specific upcoming YouTube live stream.
- Wiki: [[Livestream-Tracker#user-commands]]

| Option | Type | Description
| ---    | ---  | ---
| `video*` | String | The YouTube video ID or URL of an UPCOMING live stream to track.
| `role` | Role | If specified, a role will be pinged when this stream goes live.


### - `/untrack`:

- Untrack a currently tracked channel/feed.

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The username to untrack. @username for Twitter, Twitch username, YouTube channel ID (see wiki)
| `site` | Integer | The site name may need to be specified if it can not be inferred.


### - `/usetracker`:

- Set the default site for 'track' commands in this channel.
- Wiki: [[Configuration#overriding-the-default-website-for-track-with-the-usetracker-command]]

| Option | Type | Description
| ---    | ---  | ---
| `site*` | Integer | The site to use as the new default tracker.


### - `/setlang`:

- Change the default translation "target" language.
- Wiki: [[Translator#set-the-default-target-language-with-setlang]]

| Option | Type | Description
| ---    | ---  | ---
| `language*` | String | The new default translation "target" language.


### - `/tl`:

- Translate text between languages
- Wiki: [[Translator#-translation-commands]]

| Option | Type | Description
| ---    | ---  | ---
| `text*` | String | The text to translate.
| `to` | String | The language to translate the text into, if not specified, the server's default will be used.
| `from` | String | The language to translate the text from. Only needed if the language detection is incorrect.


### - `/translate`:

- Translate text between languages
- Wiki: [[Translator#-translation-commands]]

| Option | Type | Description
| ---    | ---  | ---
| `text*` | String | The text to translate.
| `to` | String | The language to translate the text into, if not specified, the server's default will be used.
| `from` | String | The language to translate the text from. Only needed if the language detection is incorrect.


### - `/avatar`:

- Get a user's avatar.
- Wiki: [[Discord-Info-Commands#get-user-avatar-with-avatar]]

| Option | Type | Description
| ---    | ---  | ---
| `user` | User | The user to get the avatar for. If not provided, your own avatar will be retrieved.


### - `/cleanroles`:

- Delete roles on this server with no members.
- Wiki: [[Moderation-Commands#removing-emptyunused-roles]]



### - `/top`:

- Jump to the top of a channel.



### - `/icon`:

- Get the Discord server's icon.
- Wiki: [[Discord-Info-Commands#get-server-icon]]



### - `/id`:

- Get the ID of a user in your server.
- Wiki: [[Discord-Info-Commands#get-a-users-discord-id]]

| Option | Type | Description
| ---    | ---  | ---
| `user` | User | The user to get the ID of. Defaults to yourself.


### - `/ids`:

- Get a list of role, channel, and optionally user IDs for this server.
- Wiki: [[Discord-Info-Commands#get-all-ids-in-a-server-with-ids]]

| Option | Type | Description
| ---    | ---  | ---
| `users` | True/False | Include all user IDs in this list.


### - `/server`:

- Get information on this Discord server.
- Wiki: [[Discord-Info-Commands#get-server-info-with-server]]

| Option | Type | Description
| ---    | ---  | ---
| `id` | String | The server to get info on. Defaults to current server if not specified.


### - `/timestamp`:

- Gets the timestamp from a Discord ID.
- Wiki: [[Discord-Info-Commands#-get-the-timestamp-for-any-discord-id-snowflake]]

| Option | Type | Description
| ---    | ---  | ---
| `id*` | String | The Discord ID/Snowflake to extract a timestamp from.


### - `/who`:

- Pull information on user account creation and join date.
- Wiki: [[Discord-Info-Commands#user-info-summary-server-join-time-with-who]]

| Option | Type | Description
| ---    | ---  | ---
| `user` | User | The user to pull account information for.


### - `/drag`:

- Mass-drag users between voice channels.
- Wiki: [[Moderation-Commands#mass-drag-users-in-voice-channels-with-drag]]

#### -- `/drag all`

- Move users from all voice channels into a specific voice channel.

| Option | Type | Description
| ---    | ---  | ---
| `to*` | Channel | Voice channel to drag users into.
#### -- `/drag between`

- Drag users in one voice channel to another.

| Option | Type | Description
| ---    | ---  | ---
| `from*` | Channel | Voice channel to drag users FROM.
| `to*` | Channel | Voice channel to drag users into.


### - `/temp`:

- Create a temporary voice channel.
- Wiki: [[Utility-Commands#temporary-voice-channels]]

| Option | Type | Description
| ---    | ---  | ---
| `name` | String | The name for the new voice channel.


### - `/ytlink`:

- Link a YouTube account (from your Discord profile) to FBK.



### - Message Command: `Translate Message`




### - User Command: `Get User Avatar`




### - `/twitter`:

- Configurable twitter tracker settings. Run '/twitter setup' to view all.
- Wiki: [[Twitter-Tracker#twitter-feed-notification-configuration]]

#### -- `/twitter tweets`

- Post when tracked Twitter feeds post a normal Tweet

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for tweets. Leave blank to check current value.
#### -- `/twitter retweets`

- Post when tracked feeds retweet other users

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for retweets. Leave blank to check current value.
#### -- `/twitter quotes`

- Post when tracked feeds quote tweet other users

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for quotes. Leave blank to check current value.
#### -- `/twitter replies`

- Post when tracked feeds reply to other users

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for replies. Leave blank to check current value.
#### -- `/twitter pings`

- Use the `setmention` config in this channel

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for pings. Leave blank to check current value.
#### -- `/twitter mediaonly`

- LIMIT posted Tweets to ONLY those containing media. (text-only tweets will be ignored if enabled!)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for mediaonly. Leave blank to check current value.
#### -- `/twitter translate`

- Automatically request a translation for posted tweets

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for translate. Leave blank to check current value.
#### -- `/twitter setup`

- View all twitter tracker settings and configure.

#### -- `/twitter config`

- View all twitter tracker settings and configure.



### - `/feature`:

- Configurable channel settings. Run '/feature setup' to view all.
- Wiki: [[Configuration-Commands#the-feature-command-channel-features]]

#### -- `/feature anime`

- Anime/Manga list tracking

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for anime. Leave blank to check current value.
#### -- `/feature streams`

- Livestream/video site tracking

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for streams. Leave blank to check current value.
#### -- `/feature twitter`

- Twitter feed tracking

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for twitter. Leave blank to check current value.
#### -- `/feature logs`

- Event log (See **log** command)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for logs. Leave blank to check current value.
#### -- `/feature music`

- Music bot commands

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for music. Leave blank to check current value.
#### -- `/feature tempvc`

- Temporary voice channel creation

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for tempvc. Leave blank to check current value.
#### -- `/feature restricted`

- Limit track command usage to moderators

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for restricted. Leave blank to check current value.
#### -- `/feature allowstarboarding`

- Allow this channel's messages in your starboard (if enabled)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for allowstarboarding. Leave blank to check current value.
#### -- `/feature setup`

- View all channel settings and configure.

#### -- `/feature config`

- View all channel settings and configure.



### - `/servercfg`:

- Configurable guild settings. Run '/servercfg setup' to view all.
- Wiki: [[Configuration-Commands#the-serverconfig-command]]

#### -- `/servercfg useinvites`

- Use this server's invites (required for invite-specific roles, requires Manage Server permission)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for useinvites. Leave blank to check current value.
#### -- `/servercfg auditlog`

- Use this server's audit log to enhance log info, bot requires Audit Log permission

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for auditlog. Leave blank to check current value.
#### -- `/servercfg reassignroles`

- Give users their roles back when they rejoin the server

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for reassignroles. Leave blank to check current value.
#### -- `/servercfg publish`

- Publish messages from tracked targets (e.g. YT uploads) if tracked in an Announcement channel

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for publish. Leave blank to check current value.
#### -- `/servercfg ps2commands`

- Enable PS2 commands

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for ps2commands. Leave blank to check current value.
#### -- `/servercfg setup`

- View all guild settings and configure.

#### -- `/servercfg config`

- View all guild settings and configure.



### - `/log`:

- Configurable channel log settings. Run '/log setup' to view all.
- Wiki: [[Moderation-Logs]]

#### -- `/log bots`

- Include bot actions in this channel's avatar/name/voice logs

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for bots. Leave blank to check current value.
#### -- `/log joins`

- User join log

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for joins. Leave blank to check current value.
#### -- `/log leaves`

- User part (leave) log

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for leaves. Leave blank to check current value.
#### -- `/log kicks`

- User kick log

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for kicks. Leave blank to check current value.
#### -- `/log bans`

- User ban log

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for bans. Leave blank to check current value.
#### -- `/log usernames`

- Username log

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for usernames. Leave blank to check current value.
#### -- `/log voice`

- Voice channel activity log

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for voice. Leave blank to check current value.
#### -- `/log roles`

- Role update log

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for roles. Leave blank to check current value.
#### -- `/log joinmessage`

- Join message

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for joinMessage. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: **&name&discrim** joined the server. (&mention)&new
#### -- `/log leavemessage`

- Part (leave) message

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for leaveMessage. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: **&name&discrim** left the server. (&mention)
#### -- `/log setup`

- View all channel log settings and configure.

#### -- `/log config`

- View all channel log settings and configure.



### - `/yt`:

- Configurable youtube tracker settings. Run '/yt setup' to view all.
- Wiki: [[Livestream-Tracker#-youtube-tracker-configuration-with-yt]]

#### -- `/yt streams`

- Post when tracked channels are live (yt)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for streams. Leave blank to check current value.
#### -- `/yt uploads`

- Post on video upload

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for uploads. Leave blank to check current value.
#### -- `/yt premieres`

- Post on premiere start

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for premieres. Leave blank to check current value.
#### -- `/yt creation`

- Post on initial stream creation

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for creation. Leave blank to check current value.
#### -- `/yt upcoming`

- Post when a stream is starting soon

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for upcoming. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: {empty}
#### -- `/yt upcomingchannel`

- Channel to send 'upcoming' stream messages to

| Option | Type | Description
| ---    | ---  | ---
| `value` | Channel | The new value for upcomingChannel. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: {empty}
#### -- `/yt setup`

- View all youtube tracker settings and configure.

#### -- `/yt config`

- View all youtube tracker settings and configure.



### - `/cleanreactionscfg`:

- Configurable reaction role settings. Run '/cleanreactionscfg setup' to view all.
- Wiki: [[Configuration-Commands#the-cleanreactionscfg-command]]

#### -- `/cleanreactionscfg clean`

- Remove user reactions from reaction-roles after users react and are assigned a role

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for clean. Leave blank to check current value.
#### -- `/cleanreactionscfg setup`

- View all reaction role settings and configure.

#### -- `/cleanreactionscfg config`

- View all reaction role settings and configure.



### - `/musiccfg`:

- Configurable music bot settings. Run '/musiccfg setup' to view all.
- Wiki: [[Music-Player#configuration-using-musiccfg]]

#### -- `/musiccfg deleteold`

- Delete old Now Playing bot messages

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for deleteold. Leave blank to check current value.
#### -- `/musiccfg ownerskip`

- Song owner can force skip song with fskip

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for ownerskip. Leave blank to check current value.
#### -- `/musiccfg restrictfilters`

- Restrict the usage of audio filters (volume, bass) to users who queued the track

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for restrictfilters. Leave blank to check current value.
#### -- `/musiccfg restrictseek`

- Restrict the usage of playback manipulation (ff, seek) to users who queued the track

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for restrictseek. Leave blank to check current value.
#### -- `/musiccfg forceskip`

- Skip command will instantly skip when permitted

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for forceskip. Leave blank to check current value.
#### -- `/musiccfg skipifabsent`

- Skip song if the requester is no longer in the voice channel

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for skipIfAbsent. Leave blank to check current value.
#### -- `/musiccfg skipratio`

- User ratio needed for vote skip

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for skipRatio. Leave blank to check current value.
#### -- `/musiccfg skipcount`

- User count needed for skip

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for skipCount. Leave blank to check current value.
#### -- `/musiccfg maxtracks`

- Max tracks in queue for one user (0 = unlimited)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for maxTracks. Leave blank to check current value.
#### -- `/musiccfg initialvolume`

- Default playback volume

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for initialVolume. Leave blank to check current value.
#### -- `/musiccfg volumelimit`

- Volume limit

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for volumeLimit. Leave blank to check current value.
#### -- `/musiccfg setup`

- View all music bot settings and configure.

#### -- `/musiccfg config`

- View all music bot settings and configure.



### - `/animeconfig`:

- Configurable anime list tracker settings. Run '/animeconfig setup' to view all.
- Wiki: [[Anime-List-Tracker#configuration]]

#### -- `/animeconfig new`

- Post an update message when a new item is added to a list

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for new. Leave blank to check current value.
#### -- `/animeconfig status`

- Post an update message on status change (started watching, dropped...)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for status. Leave blank to check current value.
#### -- `/animeconfig watched`

- Post an update message when an item updates (changed rating, watched x# episodes)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for watched. Leave blank to check current value.
#### -- `/animeconfig setup`

- View all anime list tracker settings and configure.

#### -- `/animeconfig config`

- View all anime list tracker settings and configure.



### - `/streamcfg`:

- Configurable livestream tracker settings. Run '/streamcfg setup' to view all.
- Wiki: [[Livestream-Tracker#stream-notification-configuration-with-streamcfg]]

#### -- `/streamcfg summary`

- Edit stream notification with a summary or VOD information rather than deleting the message

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for summary. Leave blank to check current value.
#### -- `/streamcfg thumbnail`

- Include the current stream thumbnail

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for thumbnail. Leave blank to check current value.
#### -- `/streamcfg viewers`

- Include viewer counts in summary

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for viewers. Leave blank to check current value.
#### -- `/streamcfg game`

- Include stream ending game in summary (twitch)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for game. Leave blank to check current value.
#### -- `/streamcfg pingroles`

- Use the `setmention` config in this channel

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for pingRoles. Leave blank to check current value.
#### -- `/streamcfg rename`

- Rename this channel based on live channels

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for rename. Leave blank to check current value.
#### -- `/streamcfg pinlive`

- Pin active livestreams in this channel

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for pinLive. Leave blank to check current value.
#### -- `/streamcfg notlive`

- Channel name when no streams are live

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for notlive. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: no-streams-live
#### -- `/streamcfg prefix`

- Channel name prefix

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for prefix. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: 
#### -- `/streamcfg suffix`

- Channel name suffix

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for suffix. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: 
#### -- `/streamcfg setup`

- View all livestream tracker settings and configure.

#### -- `/streamcfg config`

- View all livestream tracker settings and configure.



### - `/welcome`:

- Configurable welcome settings. Run '/welcome setup' to view all.
- Wiki: [[Welcoming-Users]]

#### -- `/welcome channel`

- Channel to send welcome messages to

| Option | Type | Description
| ---    | ---  | ---
| `value` | Channel | The new value for channel. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: {empty}
#### -- `/welcome avatar`

- Include new user's avatar in welcome embed or image

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for avatar. Leave blank to check current value.
#### -- `/welcome username`

- Include new user's username in welcome embed or image

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for username. Leave blank to check current value.
#### -- `/welcome usetagline`

- Include the 'tagline' in the message image

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for usetagline. Leave blank to check current value.
#### -- `/welcome useimagetext`

- Include the 'imagetext' in the welcome image

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for useimagetext. Leave blank to check current value.
#### -- `/welcome message`

- Text message sent when welcoming new user

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for message. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: 
#### -- `/welcome tagline`

- Welcome Tagline (included in image or embed)

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for tagline. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: WELCOME
#### -- `/welcome imagetext`

- Image message

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for imagetext. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: Welcome to the server!
#### -- `/welcome banner`

- Banner image to use for welcoming

| Option | Type | Description
| ---    | ---  | ---
| `value` | Attachment | The new value for banner. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: {empty}
#### -- `/welcome color`

- Text color on image

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for color. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: 16777215
#### -- `/welcome emoji`

- Add reaction to welcome

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for emoji. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: {empty}
#### -- `/welcome setup`

- View all welcome settings and configure.

#### -- `/welcome config`

- View all welcome settings and configure.

#### -- `/welcome test`

- Test the current welcome configuration.

#### -- `/welcome getbanner`

- Get the current welcome banner image.



### - `/starboard`:

- Configurable starboard settings. Run '/starboard setup' to view all.
- Wiki: [[Starboard]]

#### -- `/starboard channel`

- Starboard channel ID. Reset to disable starboard

| Option | Type | Description
| ---    | ---  | ---
| `value` | Channel | The new value for channel. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: {empty}
#### -- `/starboard stars`

- Stars required for a message to be put on the starboard

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for stars. Leave blank to check current value.
#### -- `/starboard removeonclear`

- Remove a message from the starboard if the star reactions are cleared by a moderator

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for removeOnClear. Leave blank to check current value.
#### -- `/starboard removeondelete`

- Remove a message from the starboard if the original message is deleted

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for removeOnDelete. Leave blank to check current value.
#### -- `/starboard mentionuser`

- Mention a user when their message is placed on the starboard

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for mentionUser. Leave blank to check current value.
#### -- `/starboard includensfw`

- Allow messages in NSFW-flagged channels to be starboarded

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for includeNSFW. Leave blank to check current value.
#### -- `/starboard emoji`

- Emoji used to add messages to the starboard

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for emoji. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: {empty}
#### -- `/starboard setup`

- View all starboard settings and configure.

#### -- `/starboard config`

- View all starboard settings and configure.

#### -- `/starboard message`

- Manually add a message to the Starboard.

| Option | Type | Description
| ---    | ---  | ---
| `id*` | String | The Discord ID of the message to add to the starboard.

