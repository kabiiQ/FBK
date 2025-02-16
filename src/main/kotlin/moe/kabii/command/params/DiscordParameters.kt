package moe.kabii.command.params

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.core.event.domain.interaction.ComponentInteractionEvent
import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.command.Interaction
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import moe.kabii.command.*
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.discord.event.interaction.ChatCommandHandler
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.FBK
import moe.kabii.util.extensions.name
import moe.kabii.util.extensions.tryBlock
import moe.kabii.util.extensions.userAddress
import moe.kabii.util.i18n.Translations
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

data class DiscordParameters (
    val handler: ChatCommandHandler,
    val event: ChatInputInteractionEvent,
    val interaction: Interaction,
    val chan: MessageChannel,
    val guild: Guild?,
    val author: User,
    val command: Command,
    val client: FBK,
    val args: ChatCommandArguments = ChatCommandArguments(event)
) {

    val subCommand by lazy { event.options[0] }

    fun subArgs(sub: ApplicationCommandInteractionOption) = ChatCommandArguments(sub)

    // Commands which require guild context and should simply error if executed in PMs can retrieve the 'target' guild
    val target: Guild by lazy {
        if(guild != null) return@lazy guild
        throw GuildTargetInvalidException("Guild context unknown.")
    }

    val isPM = guild == null

    val authorNickname by lazy {
        if(guild != null) member.displayName else author.name
    }

    val member: Member by lazy {
        author.asMember(target.id).tryBlock().orNull() ?: error("Unable to get author as member of origin guild")
    }

    val config: GuildConfiguration by lazy {
        GuildConfigurations.getOrCreateGuild(client.clientId, target.id.asLong())
    }

    suspend fun features() = config.getOrCreateFeatures(guildChan.id.asLong())

    // error if we need to verify channel permissions for targeting specific channel, but this was executed in DMs
    val guildChan: GuildChannel
        get() = (chan as? GuildChannel) ?: throw GuildTargetInvalidException("Current channel is not a Discord server channel.")

    suspend fun channelVerify(vararg permissions: Permission) = member.channelVerify(guildChan, *permissions)

    @Throws(GuildFeatureDisabledException::class)
    fun guildFeatureVerify(feature: KProperty1<GuildSettings, Boolean>, featureName: String? = null) {
        if(guild != null) {
            val name = featureName ?: feature.name
            if(!feature.get(config.guildSettings)) throw GuildFeatureDisabledException(name, "servercfg $name enable")
        } // else this is pm, allow
    }

    @Throws(ChannelFeatureDisabledException::class)
    suspend fun channelFeatureVerify(feature: KProperty1<FeatureChannel, Boolean>, featureName: String? = null, allowOverride: Boolean = true) {
        if(guild != null) {
            val features = config.options.featureChannels[chan.id.asLong()] ?: FeatureChannel(chan.id.asLong())
            val name = featureName ?: feature.name.removeSuffix("Channel")
            val permOverride = member.hasPermissions(guildChan, Permission.MANAGE_CHANNELS)
            if(!feature.get(features) && (!allowOverride || !permOverride)) throw ChannelFeatureDisabledException(name, this, feature)
        } // else this is pm, allow
    }

    /**
     * Verify a channel-specific feature is enabled in a different channel (for transferring to a new channel)
     */
    @Throws(ChannelFeatureDisabledException::class)
    fun guildChannelFeatureVerify(feature: KProperty1<FeatureChannel, Boolean>, featureName: String? = null, targetChannel: GuildMessageChannel) {
        val features = config.options.featureChannels[targetChannel.id.asLong()] ?: FeatureChannel(targetChannel.id.asLong())
        val name = featureName ?: feature.name.removeSuffix("Channel")
        if(!feature.get(features)) throw ChannelFeatureDisabledException(name, this, feature)
    }

    // basic command reply
    fun ireply(vararg embeds: EmbedCreateSpec) = event
        .reply()
        .withEmbeds(*embeds)
        .thenReturn(Unit)

    fun ereply(vararg embeds: EmbedCreateSpec) = event
        .reply()
        .withEmbeds(*embeds)
        .withEphemeral(true)
        .thenReturn(Unit)

    // listen for response to components on reply
    fun <T : ComponentInteractionEvent> listener(type: KClass<T>, restrict: Boolean = true, timeout: Duration? = null, vararg componentId: String): Flux<T> =
        event.reply
            .map(Message::getId)
            .flatMapMany { messageId ->
                event.client
                    .on(type.java)
                    .filter { interact -> componentId.contains(interact.customId) }
                    .filter { interact -> interact.messageId == messageId }
                    .filter { interact ->
                        if(restrict) {
                            if(interact.interaction.user.id == event.interaction.user.id) true
                            else {
                                // TODO nested subscribe - problematic, but I want this working for now
                                interact.reply()
                                    .withEmbeds(Embeds.error("Only **${event.interaction.user.userAddress()}** may respond to this prompt."))
                                    .withEphemeral(true)
                                    .subscribe()
                                false
                            }
                        } else true
                    }
//                    .run { if(restrict) filter { interact ->
//                        interact.interaction.user.id == event.interaction.user.id
//                    } else this  }
                    .run { if(timeout != null) timeout(timeout) else this }
                    .onErrorResume(TimeoutException::class.java) { _ -> Mono.empty() }
            }

    // internationalization
    fun i18n(identifier: String): String = selecti18nMethod(identifier)

    fun i18n(identifier: String, vararg variables: Pair<String, Any>): String = selecti18nMethod(identifier, namedVars = variables)

    fun i18n(identifier: String, vararg variables: Any): String = selecti18nMethod(identifier, orderedVars = variables)

    private fun selecti18nMethod(stringIdentifier: String, namedVars: Array<out Pair<String, Any>>? = null, orderedVars: Array<out Any>? = null): String {
        /*val useLocale = if(!isPM) Translations.locales[config.translator.guildLocale] else null
        val locale = useLocale ?: Translations.locales[Translations.defaultLocale]*/
        val locale = Translations.locales[Translations.defaultLocale]
        checkNotNull(locale) { "Missing locale ${config.translator.guildLocale} set for guild ${target.id}" }

        return if(namedVars != null) {
            // named variables
            locale.responseString(stringIdentifier, *namedVars)
        } else {
            // ordered variables or no variables
            locale.responseString(stringIdentifier, *(orderedVars.orEmpty()))
        }
    }
}