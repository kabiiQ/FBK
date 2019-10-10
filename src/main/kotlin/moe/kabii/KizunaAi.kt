package moe.kabii

import com.github.philippheuer.credentialmanager.CredentialManagerBuilder
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.auth.providers.TwitchIdentityProvider
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import discord4j.core.DiscordClientBuilder
import discord4j.core.event.domain.UserUpdateEvent
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.event.domain.channel.TextChannelDeleteEvent
import discord4j.core.event.domain.guild.*
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.lifecycle.ReconnectEvent
import discord4j.core.event.domain.message.*
import discord4j.core.event.domain.role.RoleDeleteEvent
import moe.kabii.data.Keys
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MongoDBConnection
import moe.kabii.data.relational.PostgresConnection
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.MessageHandler
import moe.kabii.discord.command.commands.BotAdminCommands
import moe.kabii.discord.command.commands.StaticCommands
import moe.kabii.discord.command.commands.audio.*
import moe.kabii.discord.command.commands.audio.search.SearchTracks
import moe.kabii.discord.command.commands.configuration.*
import moe.kabii.discord.command.commands.configuration.roles.*
import moe.kabii.discord.command.commands.configuration.setup.*
import moe.kabii.discord.command.commands.meta.BotStats
import moe.kabii.discord.command.commands.meta.CommandInfo
import moe.kabii.discord.command.commands.moderation.*
import moe.kabii.discord.command.commands.search.TwitchStreamLookup
import moe.kabii.discord.command.commands.search.Urban
import moe.kabii.discord.command.commands.trackers.TrackerCommandBase
import moe.kabii.discord.command.commands.trackers.twitch.TwitchFollow
import moe.kabii.discord.command.commands.users.ReminderCommands
import moe.kabii.discord.command.commands.utility.*
import moe.kabii.discord.command.commands.voice.TemporaryChannels
import moe.kabii.discord.event.guild.*
import moe.kabii.discord.event.user.*
import moe.kabii.discord.invite.InviteWatcher
import moe.kabii.discord.tasks.AutojoinVoice
import moe.kabii.discord.tasks.OfflineUpdateHandler
import moe.kabii.discord.tasks.RecoverQueue
import moe.kabii.discord.tasks.ReminderWatcher
import moe.kabii.discord.trackers.anime.MediaListWatcher
import moe.kabii.discord.trackers.twitch.TwitchStreamWatcher
import moe.kabii.helix.TwitchHelix
import moe.kabii.joint.commands.RandomCommands
import moe.kabii.joint.commands.TwitchInfo
import moe.kabii.joint.commands.Vinglish
import moe.kabii.net.NettyFileServer
import moe.kabii.structure.Metadata
import moe.kabii.structure.Uptime
import moe.kabii.structure.orNull
import reactor.core.publisher.Mono

fun main() {
    // init
    val mongo = MongoDBConnection
    val postgres = PostgresConnection
    val version = Metadata.current
    val keys = Keys.config

    // twitch connection
    val credential = CredentialManagerBuilder.builder().build()
    credential.registerIdentityProvider(TwitchIdentityProvider(
        Keys.config[Keys.Twitch.client],
        Keys.config[Keys.Twitch.secret],
        Keys.config[Keys.Twitch.callback]
    ))
    val oAuth = OAuth2Credential("twitch", Keys.config[Keys.Twitch.oauth])
    val twitch = TwitchClientBuilder.builder()
        .withEnableChat(true)
        .withCredentialManager(credential)
        .withChatAccount(oAuth)
        .build()

    val messageHandler = MessageHandler(twitch)

    // uhh this can probably be done reflectively eventually but as-is is a quick method for maintainable... like disabling a module
    messageHandler.apply {
        // containers
        register(StaticCommands)
        register(ChannelFeatures)
        register(BotAdminCommands)
        register(RandomCommands)
        register(DummyCommands)
        register(GuildOptions)
        register(Preferences)
        register(Purge)
        register(TwitchInfo)
        register(TrackerCommandBase)
        register(JoinRole)
        register(VoiceRole)
        register(SelfRoles)
        register(SnowflakeUtil)
        register(EmojiUtil)
        register(TemporaryChannels)
        register(BotStats)
        register(UserModeration)
        register(CommandFilters)
        register(SelfRoleCommands)
        register(TwitchFollow)
        register(RoleUtils)
        register(MusicConfig)
        register(QueueTracks)
        register(QueueInfo)
        register(PlaybackState)
        register(QueueSkip)
        register(PlaybackSeek)
        register(BotState)
        register(QueueEdit)
        register(SearchTracks)
        register(SetFollow)
        register(RoleReactions)
        register(BotUtil)
        register(ExclusiveRoles)
        register(UserUtil)
        register(ReminderCommands)

        // single commands
        register(Vinglish)
        register(AutoRole)
        register(MentionRole)
        register(EditLog)
        register(FeatureConfig)
        register(Drag)
        register(Urban)
        register(RandomRoleColor)
        register(TwitchStreamLookup)
        register(CommandInfo)
        register(GuildFeatures)

        register(object : Command("test") {
            init {
                discord {
                    embed("Hello World!").subscribe()
                }
            }
        })
    }

    // discord connection
    val discord = DiscordClientBuilder(Keys.config[Keys.Discord.token]).build()

    // discord audio setup
    val audio = AudioManager

    // task threads
    var init = false
    val listWatcher = MediaListWatcher(discord)
    val streamWatcher = TwitchStreamWatcher(discord)
    val reminderWatcher = ReminderWatcher(discord)

    // discord4j event listeners
    val events = discord.eventDispatcher
    val onMessage = events.on(MessageCreateEvent::class.java)
        .filter { event -> event.message.author.orNull()?.isBot?.not() ?: false }
        .doOnNext(messageHandler::handleDiscord)

    val onGuildReady = events.on(ReadyEvent::class.java)
        .map { event -> event.guilds.size }
        .doOnNext { count ->
            println("Connecting to $count guilds.")
        }
        .flatMap { count ->
            val recoverQueue = RecoverQueue()
            events.on(GuildCreateEvent::class.java)
                .take(count.toLong())
                .map(GuildCreateEvent::getGuild)
                .doOnNext(OfflineUpdateHandler::runChecks)
                .doOnNext(AutojoinVoice::autoJoin)
                .doOnNext { guild ->
                    InviteWatcher.updateGuild(guild)
                    recoverQueue.recover(guild)
                    println("Connected to guild ${guild.name}")
                }
        }

    val onInitialReady = events.on(ReadyEvent::class.java)
        .take(1)
        .map { event -> event.guilds.size }
        .flatMap { count ->
            events.on(GuildCreateEvent::class.java)
                .take(count.toLong())
                .collectList()
        }
        .doOnNext { _ ->
            // init tasks
            Uptime
            listWatcher.start()
            streamWatcher.start()
            reminderWatcher.start()
        }

    val onReactionAdd = events.on(ReactionAddEvent::class.java)
            .doOnNext(ReactionHandler::handleReactionAdded)
    val onReactionAdd2 = events.on(ReactionAddEvent::class.java)
        .doOnNext(ReactionRoleHandler::handle)
    val onReactionRemove = events.on(ReactionRemoveEvent::class.java)
        .doOnNext(ReactionHandler::handleReactionRemoved)
    val onJoin = events.on(MemberJoinEvent::class.java)
        .map(MemberJoinEvent::getMember)
        .doOnNext { member -> JoinHandler.handle(member) }
    val onPart = events.on(MemberLeaveEvent::class.java)
            .doOnNext { event ->
                PartHandler.handle(event.guildId, event.user, event.member.orNull())
            }
    val onVoiceUpdate = events.on(VoiceStateUpdateEvent::class.java)
            .doOnNext(VoiceMoveHandler::handle)
    val onUserUpdate = events.on(UserUpdateEvent::class.java)
        .doOnNext(UserUpdateHandler::handle)
    val onMessageUpdate = events.on(MessageUpdateEvent::class.java)
        .doOnNext(MessageEditHandler::handle)
    val onMessageDelete = events.on(MessageDeleteEvent::class.java)
        .doOnNext(MessageDeletionHandler::handleDelete)
    val onMessageBulkDelete = events.on(MessageBulkDeleteEvent::class.java)
        .doOnNext(MessageDeletionHandler::handleBulkDelete)
    val onTextChannelDelete = events.on(TextChannelDeleteEvent::class.java)
        .doOnNext(ChannelDeletionHandler::handle)
    val onGatewayReconnection = events.on(ReconnectEvent::class.java)
        .doOnNext { Uptime.update() }
    val onMemberUpdate = events.on(MemberUpdateEvent::class.java)
        .doOnNext(MemberUpdateHandler::handle)
    val onRoleDeletion = events.on(RoleDeleteEvent::class.java)
        .doOnNext(RoleDeletionHandler::handle)
    val onGuildUpdate = events.on(GuildUpdateEvent::class.java)
        .doOnNext(GuildUpdateHandler::handle)

    // twitch4j event listeners
    val onTwitchMessage = twitch.eventManager.onEvent(ChannelMessageEvent::class.java)
            .filter { it.user.name.toLowerCase() != "ai_kizuna" }
            .doOnNext(messageHandler::handleTwitch)

    // subscribe to bot lifetime events
    Mono.`when`(
        onMessage, onJoin, onPart, onVoiceUpdate,
        onUserUpdate, onMessageUpdate, onMessageDelete, onMessageBulkDelete,
        onTextChannelDelete, onGatewayReconnection, onMemberUpdate,
        onTwitchMessage, onRoleDeletion, onGuildUpdate,
        onReactionAdd, onReactionRemove, onReactionAdd2, onGuildReady,
        onInitialReady)
        .onErrorResume { t ->
            println("Uncaught exception: ${t.message}") // todo log
            t.printStackTrace()
            Mono.empty()
        }
        .subscribe()

    // start file server
    NettyFileServer.server.start()

    // join any linked channels on twitch IRC
    GuildConfigurations.guildConfigurations.values
        .mapNotNull { it.options.linkedTwitchChannel?.twitchid }
        .let(TwitchHelix::getUsers).values
        .mapNotNull { user -> user.orNull()?.login }
        .forEach(twitch.chat::joinChannel)

    // login
    discord.login().subscribe()
}