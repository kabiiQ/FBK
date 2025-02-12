package moe.kabii.net.api.commands

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.channel.TextChannel
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.params.ExternalParameters
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.log
import moe.kabii.util.extensions.stackTraceString

/**
 * External command execution API - for internal use, little error handling or security
 * API is not enabled on public bot nor port exposed on bot instance where it is used
 */
class ExternalCommandsService(val instances: DiscordInstances) {

    private val port = 8020

    init {
        LOG.info("Internal API: ExternalCommands - server binding to port $port")
    }

    val server = embeddedServer(Netty, port = port) {
        routing {
            post("/command") {
                log("POST to ExternalCommands API /command: $port")

                val body = call.receiveText()
                LOG.info(body)
                val command = try {
                    ExternalCommand.adapter.fromJson(body)!!
                } catch(e: Exception) {
                    call.response.status(HttpStatusCode.BadRequest)
                    return@post
                }

                LOG.info(command.toString())

                try {
                    // currently always using instance #1, feature shouldn't be used on scaled bots anyways
                    val fbk = instances[1]
                    val user = fbk.client
                        .getUserById(Snowflake.of(command.userId))
                        .awaitSingle()
                    val channel = fbk.client
                        .getChannelById(Snowflake.of(command.channelId))
                        .ofType(TextChannel::class.java)
                        .awaitSingle()

                    val params = ExternalParameters(instances, command, fbk, user, channel)
                    command.executable().executeExternal!!(params)
                    call.response.status(HttpStatusCode.OK)
                } catch(e: Exception) {
                    LOG.info("External command execution errored: ${e.message}")
                    LOG.debug(e.stackTraceString)
                    call.respondText(
                        text = e.message ?: "An error occured",
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
        }
    }
}