# ℹ Command List

This page is an automatically generated list of all bot commands with a link to their wiki page for further information.

> 🔍 Commands labeled as "User Command" are accessible in Discord by the context or "right-click" menu on a user. 
> 🔍 Commands labeled as "Message Command" are found in the context menu on messages.

> ❓ For all command options, `*` indicates it is required to run the command. All other options are "optional" and are not required in the Discord client, but may be useful to change the behavior of some commands.

### - Message Command: `Translate Message`




### - `/randomizecolor`:

- Selects a randomized color for a role.
- Wiki: [[Moderation-Commands#randomizing-a-roles-color-with-randomizecolor]]

| Option | Type | Description
| ---    | ---  | ---
| `role*` | Role | The role to change the color of.


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


### - `/garble`:

- Garble a text message like you can't consistently type.
- Wiki: [[RNG-Commands#garble-text-]]

| Option | Type | Description
| ---    | ---  | ---
| `text*` | String | The text to garble.


### - `/coinflip`:

- Flip a coin!
- Wiki: [[RNG-Commands#the-coinflip-command]]



### - `/pick`:

- Pick an option from a list.
- Wiki: [[RNG-Commands#the-pick-command]]

| Option | Type | Description
| ---    | ---  | ---
| `list` | String | Space-separated list of "options" to pick randomly from.


### - `/emojify`:

- Convert some text to regional indicator emojis.
- Wiki: [[RNG-Commands#text-to-regional-indicator-emoji-]]

| Option | Type | Description
| ---    | ---  | ---
| `text*` | String | The text to convert to emojis.


### - `/ask`:

- Ask the bot a question (get a random Magic 8 Ball response)
- Wiki: [[RNG-Commands#the-ask-command]]

| Option | Type | Description
| ---    | ---  | ---
| `question` | String | The "question".


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


### - `/translate`:

- Translate text between languages
- Wiki: [[Translator#-translation-commands]]

| Option | Type | Description
| ---    | ---  | ---
| `text*` | String | The text to translate.
| `to` | String | The language to translate the text into, if not specified, the server's default will be used.
| `from` | String | The language to translate the text from. Only needed if the language detection is incorrect.
| `translator` | String | The preferred translator to complete this request. Changes based on availability.


### - `/tl`:

- Translate text between languages
- Wiki: [[Translator#-translation-commands]]

| Option | Type | Description
| ---    | ---  | ---
| `text*` | String | The text to translate.
| `to` | String | The language to translate the text into, if not specified, the server's default will be used.
| `from` | String | The language to translate the text from. Only needed if the language detection is incorrect.
| `translator` | String | The preferred translator to complete this request. Changes based on availability.


### - `/remindcancel`:

- Cancel an existing reminder that is no longer needed.
- Wiki: [[Reminders]]

| Option | Type | Description
| ---    | ---  | ---
| `reminder*` | Integer | The ID of the reminder you would like to cancel, found where you created the reminder.


### - `/remind`:

- Create a reminder to be sent to you in the near future.
- Wiki: [[Reminders]]

| Option | Type | Description
| ---    | ---  | ---
| `time*` | String | The time until this reminder should be sent. Examples: 2m or 6h or 1w
| `message` | String | What you would like to be reminded about.
| `dm` | True/False | If you would like this reminder sent via DM rather than in this channel.


### - `/xkcd`:

- Look up a comic from xkcd.
- Wiki: [[Lookup-Commands#xkcd-comics-xkcd]]

| Option | Type | Description
| ---    | ---  | ---
| `id` | Integer | The ID of the xkcd comic to retrieve. If not provided, returns the current comic.


### - `/ttv`:

- Look up current information on a Twitch livestream.
- Wiki: [[Lookup-Commands#twitch-stream-lookup-ttv]]

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The Twitch username to look up.


### - `/skeb`:

- Search skeb.jp for a user's profile.
- Wiki: [[Lookup-Commands#skeb-profile-lookup-skeb]]

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The skeb username to look up


### - `/ud`:

- Look up a term on Urban Dictionary.
- Wiki: [[Lookup-Commands#urbandictionary-lookup-ud]]

| Option | Type | Description
| ---    | ---  | ---
| `term*` | String | The term to look up on Urban Dictionary.


### - `/calc`:

- Perform a calculation using the WolframAlpha knowledge engine.
- Wiki: [[Lookup-Commands#wolframalpha-queries-calc]]

| Option | Type | Description
| ---    | ---  | ---
| `query*` | String | The calculation to perform.


### - `/twittervid`:

- Gets a playable video from a Tweet.

| Option | Type | Description
| ---    | ---  | ---
| `url*` | String | The Tweet URL to check for a video.


### - `/editmention`:

- Configure a role to be mentioned when a tracked channel goes live. Edits the specified options.

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The tracked stream/twitter feed pings to edit
| `role` | Role | The role that should be pinged. If empty, any configured role will no longer be pinged.
| `site` | Integer | The site name may need to be specified if it can not be inferred.
| `text` | String | Text to be included along with the ping.
| `membershiprole` | Role | A role to ping for YouTube member-only streams. Can be the same role as regular streams or none.
| `membershiptext` | String | Text to be included for YouTube member-only streams.
| `upcomingrole` | Role | A role to ping for 'upcoming' YouTube streams (must be configured to be posted)
| `creationrole` | Role | A role to ping when YouTube streams are initially scheduled (must be configured to be posted)
| `alternateuploadrole` | Role | An alternate role pinged for YouTube uploads/premieres. Most users should not touch this setting.
| `alternatepremiererole` | Role | An alternate role pinged for YouTube premieres. Most users should not touch this setting.
| `alternateshortsrole` | Role | An alternate role pinged for 'short' YouTube uploads. Most users should not touch this setting.
| `twittercolor` | String | A custom color to apply to Tweet messages for this feed.


### - `/setmention`:

- REMOVES PREVIOUS CONFIGS. Configure a role to be mentioned when a tracked channel goes live.
- Wiki: [[Livestream-Tracker#-pinging-a-role-with-setmention]]

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The tracked stream/twitter feed that should send a ping
| `role` | Role | The role that should be pinged. If empty, any configured role will no longer be pinged.
| `site` | Integer | The site name may need to be specified if it can not be inferred.
| `text` | String | Text to be included along with the ping. If empty, any existing text will be removed.
| `copyfrom` | String | A stream tracked in this channel to copy mention settings from. Applied before all other settings.
| `membershiprole` | Role | A role to ping for YouTube member-only streams. Can be the same role as regular streams or none.
| `membershiptext` | String | Text to be included for YouTube member-only streams.
| `upcomingrole` | Role | A role to ping for 'upcoming' YouTube streams (must be configured to be posted)
| `creationrole` | Role | A role to ping when YouTube streams are initially scheduled (must be configured to be posted)
| `alternateuploadrole` | Role | An alternate role pinged for YouTube uploads/premieres. Most users should not touch this setting.
| `alternatepremiererole` | Role | An alternate role pinged for YouTube premieres. Most users should not touch this setting.
| `alternateshortsrole` | Role | An alternate role pinged for 'short' YouTube uploads. Most users should not touch this setting.
| `twittercolor` | String | A custom color to apply to Tweet messages for this feed.


### - `/usetracker`:

- Set the default site for 'track' commands in this channel.
- Wiki: [[Configuration#overriding-the-default-website-for-track-with-the-usetracker-command]]

| Option | Type | Description
| ---    | ---  | ---
| `site*` | Integer | The site to use as the new default tracker.


### - `/tracked`:

- List targets that are currently tracked in this channel.

| Option | Type | Description
| ---    | ---  | ---
| `site` | Integer | If specified, only tracked targets for this specific site will be listed.


### - `/untrack`:

- Untrack a currently tracked channel/feed.

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The username to untrack. @username for Twitter, Twitch username, YouTube channel ID (see wiki)
| `site` | Integer | The site name may need to be specified if it can not be inferred.
| `moveto` | Channel | A Discord channel to move this tracked feed into, after removing it from the current channel.


### - `/trackvid`:

- Track a specific upcoming YouTube live stream.
- Wiki: [[Livestream-Tracker#user-commands]]

| Option | Type | Description
| ---    | ---  | ---
| `video*` | String | The YouTube video ID or URL of an UPCOMING live stream to track.
| `usepings` | String | A tracked channel to use the ping settings from when this video goes live.


### - `/track`:

- Track a streamer's channel (yt, twitch, twitcasting) or Twitter feed. See the wiki for more details.
- Wiki: [[Livestream-Tracker]]

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The username to track. @username for Twitter, username for Twitch, channel ID for YouTube (see wiki)
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



### - `/getmention`:

- Test the configured '/setmention' role ping for a tracked Twitter/livestream feed. (WILL PING ROLES)
- Wiki: [[Livestream-Tracker#-pinging-a-role-with-setmention]]

| Option | Type | Description
| ---    | ---  | ---
| `username*` | String | The tracked stream/twitter feed that should send a ping
| `site` | Integer | The site name may need to be specified if it can not be inferred.


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


### - `/cleanroles`:

- Delete roles on this server with no members.
- Wiki: [[Moderation-Commands#removing-emptyunused-roles]]



### - `/avatar`:

- Get a user's avatar.
- Wiki: [[Discord-Info-Commands#get-user-avatar-with-avatar]]

| Option | Type | Description
| ---    | ---  | ---
| `user` | User | The user to get the avatar for. If not provided, your own avatar will be retrieved.


### - `/timestamp`:

- Gets the timestamp from a Discord ID.
- Wiki: [[Discord-Info-Commands#-get-the-timestamp-for-any-discord-id-snowflake]]

| Option | Type | Description
| ---    | ---  | ---
| `id*` | String | The Discord ID/Snowflake to extract a timestamp from.


### - `/id`:

- Get the ID of a user in your server.
- Wiki: [[Discord-Info-Commands#get-a-users-discord-id]]

| Option | Type | Description
| ---    | ---  | ---
| `user` | User | The user to get the ID of. Defaults to yourself.


### - `/top`:

- Jump to the top of a channel.



### - `/icon`:

- Get the Discord server's icon.
- Wiki: [[Discord-Info-Commands#get-server-icon]]



### - `/who`:

- Pull information on user account creation and join date.
- Wiki: [[Discord-Info-Commands#user-info-summary-server-join-time-with-who]]

| Option | Type | Description
| ---    | ---  | ---
| `user` | User | The user to pull account information for.


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


### - `/connect4`:

- Connect 4 has moved! Use /game connect4



### - `/transferdata`:

- Transfer all data if you are switching FBK instances. Does not matter if run on the old or new bot.



### - `/ping`:

- Test FBK's ability to respond to you on Discord.
- Wiki: [[Bot-Meta-Commands#ping]]



### - `/botinfo`:

- Display basic bot information: uptime, version
- Wiki: [[Bot-Meta-Commands#bot-info-command]]



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



### - `/datadeletionrequest`:

- (Privacy) Delete any collected data on your Discord user account or server.

#### -- `/datadeletionrequest user`

- Delete ALL INFORMATION concerning your USER ACCOUNT. Irreversible, breaking operation.

#### -- `/datadeletionrequest server`

- Delete ALL INFORMATION concerning this Discord server. Irreversible, breaking operation.



### - `/ytlink`:

- Link a YouTube account (from your Discord profile) to FBK.



### - `/configs`:

- List available FBK configuration commands.
- Wiki: [[https://github.com/kabiiQ/FBK/wiki/Configuration-Commands]]



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



### - `/welcomebanners`:

- Edit banner images used for welcoming users

#### -- `/welcomebanners add`

- Add a welcome banner.

| Option | Type | Description
| ---    | ---  | ---
| `image*` | Attachment | The new banner image to add. See wiki for banner requirements.
#### -- `/welcomebanners list`

- View currently uploaded welcome banners.

#### -- `/welcomebanners remove`

- Delete an uploaded welcome banner.

| Option | Type | Description
| ---    | ---  | ---
| `file*` | String | The uploaded banner image to delete.


### - `/channels`:

- List channels in this server with enabled features.
- Wiki: [[Configuration-Commands#the-feature-command-channel-features]]



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



### - `/temp`:

- Create a temporary voice channel.
- Wiki: [[Utility-Commands#temporary-voice-channels]]

| Option | Type | Description
| ---    | ---  | ---
| `name` | String | The name for the new voice channel.


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


### - User Command: `Get User Avatar`




### - `/languagecfg`:

- Configurable language settings settings. Run '/languagecfg config' to view all.

#### -- `/languagecfg ephemeral`

- Only display "Translate Message" output to command user

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for ephemeral. Leave blank to check current value.
#### -- `/languagecfg noretweets`

- Skip low-quality translation of Retweets entirely.

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for noretweets. Leave blank to check current value.
#### -- `/languagecfg targetlang`

- Default target language for translations

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for targetlang. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: en
#### -- `/languagecfg locale`

- Language/locale that FBK should use for this Discord server

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for locale. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: en
#### -- `/languagecfg config`

- View all language settings settings and configure.

#### -- `/languagecfg all`

- View all language settings settings and configure.



### - `/feature`:

- Configurable channel settings. Run '/feature config' to view all.
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
#### -- `/feature config`

- View all channel settings and configure.

#### -- `/feature all`

- View all channel settings and configure.



### - `/servercfg`:

- Configurable server settings settings. Run '/servercfg config' to view all.
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
#### -- `/servercfg config`

- View all server settings settings and configure.

#### -- `/servercfg all`

- View all server settings settings and configure.



### - `/log`:

- Configurable channel log settings. Run '/log config' to view all.
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
#### -- `/log config`

- View all channel log settings and configure.

#### -- `/log all`

- View all channel log settings and configure.



### - `/yt`:

- Configurable youtube tracker settings. Run '/yt config' to view all.
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

- Post on initial stream creation (when the stream is first scheduled)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for creation. Leave blank to check current value.
#### -- `/yt membervideos`

- Include membership-only videos/streams in this channel (Leave this enabled for most servers)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for memberVideos. Leave blank to check current value.
#### -- `/yt publicvideos`

- Include non-membership videos/streams in this channel (Leave this enabled for most servers)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for publicVideos. Leave blank to check current value.
#### -- `/yt includeshorts`

- Include uploads under 60 seconds in this channel (Leave this enabled for most servers)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for includeShorts. Leave blank to check current value.
#### -- `/yt includenonshorts`

- Include uploads over 60 seconds in this channel (Leave this enabled for most servers)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for includeNonShorts. Leave blank to check current value.
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
#### -- `/yt config`

- View all youtube tracker settings and configure.

#### -- `/yt all`

- View all youtube tracker settings and configure.



### - `/cleanreactionscfg`:

- Configurable reaction role settings. Run '/cleanreactionscfg config' to view all.
- Wiki: [[Configuration-Commands#the-cleanreactionscfg-command]]

#### -- `/cleanreactionscfg clean`

- Remove user reactions from reaction-roles after users react and are assigned a role

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for clean. Leave blank to check current value.
#### -- `/cleanreactionscfg config`

- View all reaction role settings and configure.

#### -- `/cleanreactionscfg all`

- View all reaction role settings and configure.



### - `/animecfg`:

- Configurable anime list tracker settings. Run '/animecfg config' to view all.
- Wiki: [[Anime-List-Tracker#configuration]]

#### -- `/animecfg new`

- Post an update message when a new item is added to a list

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for new. Leave blank to check current value.
#### -- `/animecfg status`

- Post an update message on status change (started watching, dropped...)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for status. Leave blank to check current value.
#### -- `/animecfg watched`

- Post an update message when an item updates (changed rating, watched x# episodes)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for watched. Leave blank to check current value.
#### -- `/animecfg config`

- View all anime list tracker settings and configure.

#### -- `/animecfg all`

- View all anime list tracker settings and configure.



### - `/streamcfg`:

- Configurable livestream tracker settings. Run '/streamcfg config' to view all.
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
#### -- `/streamcfg korotagger`

- Send the video URL as plain text for YouTube livestreams (for KoroTagger compatibility)

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for korotagger. Leave blank to check current value.
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
#### -- `/streamcfg events`

- Schedule an event on Discord for live and upcoming streams tracked in this channel

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for events. Leave blank to check current value.
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
#### -- `/streamcfg config`

- View all livestream tracker settings and configure.

#### -- `/streamcfg all`

- View all livestream tracker settings and configure.



### - `/welcome`:

- Configurable welcome settings. Run '/welcome config' to view all.
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
#### -- `/welcome textoutline`

- Use a black outline to make image text more visible

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for textoutline. Leave blank to check current value.
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
#### -- `/welcome config`

- View all welcome settings and configure.

#### -- `/welcome all`

- View all welcome settings and configure.

#### -- `/welcome test`

- Test the current welcome configuration.



### - `/apikeys`:

- Configurable Custom API Keys settings. Run '/apikeys config' to view all.

#### -- `/apikeys deepl`

- DeepL API Free

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for deepl. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: {empty}
#### -- `/apikeys config`

- View all Custom API Keys settings and configure.

#### -- `/apikeys all`

- View all Custom API Keys settings and configure.



### - `/twitterping`:

- Configurable twitter feed ping settings. Run '/twitterping config' to view all.
- Wiki: [[Twitter-Tracker#changing-which-tweets-will-ping-with-twitterping-config]]

#### -- `/twitterping pings`

- (Legacy) Use the `setmention` config. Must be enabled for any pings to occur in this channel.

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for pings. Leave blank to check current value.
#### -- `/twitterping pingtweets`

- Mention the configured role for normal Tweets.

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for pingtweets. Leave blank to check current value.
#### -- `/twitterping pingquotes`

- Mention for Quote Tweets.

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for pingquotes. Leave blank to check current value.
#### -- `/twitterping pingretweets`

- Mention for Retweets.

| Option | Type | Description
| ---    | ---  | ---
| `value` | Integer | The new value for pingretweets. Leave blank to check current value.
#### -- `/twitterping config`

- View all twitter feed ping settings and configure.

#### -- `/twitterping all`

- View all twitter feed ping settings and configure.



### - `/twitter`:

- Configurable twitter tracker settings. Run '/twitter config' to view all.
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
#### -- `/twitter customurl`

- Post custom Twitter links, overriding the standard FBK embed. (Embedded translations will not be ...

| Option | Type | Description
| ---    | ---  | ---
| `value` | String | The new value for customurl. Leave blank to check current value.
| `reset` | True/False | Reset this option its default value: {empty}
#### -- `/twitter config`

- View all twitter tracker settings and configure.

#### -- `/twitter all`

- View all twitter tracker settings and configure.

