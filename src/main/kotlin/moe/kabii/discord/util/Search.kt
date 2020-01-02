package moe.kabii.discord.util

import discord4j.core.`object`.entity.*
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.DiscordMessageHandler
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import java.util.*

object Search {
    private fun clean(str: String) = str.trim().toLowerCase().replace(" ", "").replace(",", "") // comma to help assist matching, most commands accept comma separation so we'll remove them in the actual role names too

    private val roleMention = Regex("(<@&)([0-9]+)(>)")
    suspend fun roleByNameOrID(param: DiscordParameters, query: String): Role? {
        // check if this a role id
        val snowflake = query.toLongOrNull()?.snowflake
        if (snowflake != null) {
            val role = param.target.getRoleById(snowflake).tryBlock()
            if (role is Ok) return role.value // if no match just continue, role name could still be a number
        }
        // check if this is a role mention
        val mention = roleMention.find(query)?.groups?.get(2)?.value?.toLongOrNull()?.snowflake
        if(mention != null) {
            val role = param.target.getRoleById(mention).tryBlock()
            if (role is Ok) return role.value
        }

        suspend fun prompt(options: List<Role>): Role? {
            // 1: role name
            val roles = options
                .filter { role -> !role.isEveryone } // never put @everyone in a prompt as even sending it plaintext seems to cause a mention? - this is specific to @everyone
                .mapIndexed { id, role -> "${id + 1}: ${role.name} (${role.id.asString()})" }
                .joinToString("\n")
            val prompt = param.embed {
                setTitle("Multiple roles found matching \"$query\". Please select one of the following roles with its ID number (1-${options.size}):")
                setDescription(roles)
            }.block()
            val range = 0L..options.size // adding/subtracting here to give the user a 1-indexed interface
            val input = param.getLong(range, prompt, timeout = 240000L)
            prompt.delete().subscribe()
            return if (input != null) {
                if(input == 0L) null else {
                    val index = input - 1
                    options[index.toInt()]
                }
            } else null
        }
        val matchTo = clean(query)

        // get all roles and find partial matches. then check exact matches as they are a subset of partial matches
        val partial = param.target.roles
            .filter { role -> clean(role.name).contains(matchTo) }
            .collectList().block()
        val exact = partial
            .filter { role -> role.name.toLowerCase() == query.toLowerCase() }

        // exact matches are preferred if there were any found, check first
        when {
            exact.size == 1 -> return exact[0]
            exact.size > 1 -> {
                return prompt(exact)
            }
        }

        return when (partial.size) {
            0 -> null
            1 -> partial[0]
            else -> {
                prompt(partial)
            }
        }
    }

    val channelMention = Regex("(<#)([0-9]+)(>)")
    inline fun <reified R: GuildChannel> channelByID(param: DiscordParameters, query: String): R? {
        // check if this is a channel id
        val client = param.event.client
        val snowflake = query.toLongOrNull()?.snowflake
        if(snowflake != null) {
            val channel = client.getChannelById(snowflake).tryBlock()
            if(channel is Ok) return channel.value as? R
        }
        // channel mention
        val mention = channelMention.find(query)?.groups?.get(2)?.value?.toLongOrNull()?.snowflake
        return mention?.run { client.getChannelById(this).tryBlock().orNull() as? R? }
    }

    fun commandByAlias(handler: DiscordMessageHandler, name: String, bypassExempt: Boolean = false): Command? = handler.manager.commands.find { command ->
        val allowed = if(bypassExempt) true else !command.commandExempt
        allowed && command.aliases.contains(name.toLowerCase())
    }

    suspend fun user(param: DiscordParameters, query: String, guildContext: Guild? = null): User? {
        // try to get user first, they don't need to belong to the guild. we can only do this by ID.
        val snowflake = query.toLongOrNull()?.snowflake
        if(snowflake != null) {
            val user = param.event.client.getUserById(snowflake).tryBlock()
            if(user is Ok) return user.value
        }
        // try to match user by name if guild context is provided
        return if(guildContext != null) {
            memberByNameOrID(param, guildContext, query)
        } else null
    }

    private val userMention = Regex("(<@!?)([0-9]+)(>)")
    private suspend fun memberByNameOrID(param: DiscordParameters, target: Guild, query: String): Member? {
        // check if this is a user ID
        val snowflake = query.toLongOrNull()?.snowflake
        if(snowflake != null) {
            val member = target.getMemberById(snowflake).tryBlock()
            if(member is Ok) return member.value
        }
        // check if this is a user mention
        val mention = userMention.find(query)?.groups?.get(2)?.value?.toLongOrNull()?.snowflake
        if(mention != null) {
            return target.getMemberById(mention).tryBlock().orNull()
        }

        suspend fun prompt(options: List<Member>): Member? {
            val members = options
                .mapIndexed { id, member -> "${id + 1}: ${member.username}#${member.discriminator} (${member.id.asString()})" }
                .joinToString("\n")
            val prompt = param.embed {
                setTitle("Multiple members found matching \"$query\". Please select one of the following roles with its ID number (1-${options.size}):")
                setDescription(members)
            }.block()
            val range = 0L..options.size
            val input = param.getLong(range, prompt, timeout = 240000L)
            return if(input != null) {
                if (input == 0L) {
                    prompt.delete().subscribe()
                    null
                } else {
                    val index = input - 1
                    options[index.toInt()]
                }
            } else {
                prompt.delete().subscribe()
                null
            }
        }

        val matchTo = clean(query)
        fun match(str: String) = clean(str).contains(matchTo)

        val partial = target.members
            .distinct(Objects::hashCode)
            .filter { member ->
                // checking nickname + username. 'display name' is not what we want here as I often type a username rather than current nickname
                if(member.nickname.map { nick -> match(nick) }.orElse(false))
                    return@filter true
                match(member.username)
            }
            .collectList().block()
        val exact = partial
            .filter { member ->
                if(member.nickname.map { nick -> nick.toLowerCase() == query.toLowerCase() }.orElse(false))
                    return@filter true
                member.username.toLowerCase() == query.toLowerCase()
            }

        when {
            exact.size == 1 -> return exact[0]
            exact.size > 1 -> {
                return prompt(exact)
            }
        }

        return when(partial.size) {
            0 -> null
            1 -> partial[0]
            else -> {
                prompt(partial)
            }
        }
    }
}