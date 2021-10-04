package moe.kabii.discord.trackers.ps2.wss

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.flat.Keys
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.ps2.PS2Tracks
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.trackers.TrackerUtil
import moe.kabii.discord.trackers.ps2.store.PS2DataCache
import moe.kabii.discord.trackers.ps2.store.PS2Faction
import moe.kabii.util.extensions.*
import org.jetbrains.exposed.sql.and
import java.net.URI
import java.time.Duration

class PS2EventStream(val discord: GatewayDiscordClient) : Runnable {

    private val dbScope = CoroutineScope(DiscordTaskPool.ps2DBThread + CoroutineName("PS2-WSS-DB") + SupervisorJob())
    private val serviceId = Keys.config[Keys.Planetside.censusId]

    override fun run() {
        val uri = URI("wss://push.planetside2.com/streaming?environment=ps2&service-id=s:$serviceId")

        applicationLoop {
            LOG.info("PS2 WebSocket RESET")
            // connect to ps2 websocket api
            try {
                val channel = Channel<WSSEvent>()
                val wssClient = PS2WebSocketClient(channel, uri)
                wssClient.connectWait()

                // compile and send initial subscriptions
                val subscription = WSSEventSubscription.raw(
                    eventNames = listOf(
                        /* "FacilityControl", */ "ContinentUnlock","ContinentLock",
                        "PlayerLogin", "PlayerLogout"),
                    worlds = listOf("all")
                )
                wssClient.send(subscription.toJson())

                for(event in channel) {

                    dbScope.launch {
                        propagateTransaction {
                            try {
                                when(event) {
                                    is WSSEvent.PlayerLog -> onPlayerLog(event)
                                    is WSSEvent.ContinentUpdate -> onContinentUpdate(event)
                                    is WSSEvent.FacilityControl -> onBaseCap(event)
                                }
                            } catch(e: Exception) {
                                LOG.warn("Error in handling PS2 event notification: ${e.message}")
                                LOG.debug(e.stackTraceString)
                            }
                        }
                    }

                }

            } catch(e: Exception) {
                LOG.error("PS2 Websocket connection aborted.")
                LOG.info(e.stackTraceString)
            }
            delay(Duration.ofSeconds(10))
            // retry
        }
    }

    private suspend fun getChannel(target: PS2Tracks.TrackTarget): MessageChannel? {
        val guildId = target.discordChannel.guild?.guildID
        val channelId = target.discordChannel.channelID
        if(guildId != null) {
            val enabled = GuildConfigurations.guildConfigurations[guildId]
                ?.options?.featureChannels?.get(channelId)?.ps2Channel
            if(enabled != true) return null
        }
        return try {
            discord.getChannelById(target.discordChannel.channelID.snowflake)
                .ofType(MessageChannel::class.java)
                .awaitSingle()
        } catch(e: Exception) {
            if(e is ClientException) {
                if(e.status.code() == 403) {
                    LOG.warn("Unable to send to Discord channel '$channelId' for YT notification. Disabling feature in channel. PS2EventStream.java")
                    TrackerUtil.permissionDenied(discord, guildId, channelId, FeatureChannel::ps2Channel, target::delete)
                } else if(e.status.code() == 404) {
                    LOG.warn("Untracking PS2 target ${target.type}:${target.censusId} in $channelId as the channel has been deleted.")
                    target.delete()
                }
            }
            throw e
        }
    }

    @WithinExposedContext
    suspend fun onPlayerLog(log: WSSEvent.PlayerLog) {
        val targets = PS2Tracks.TrackTarget.find {
            PS2Tracks.TrackTargets.type eq PS2Tracks.PS2EventType.PLAYER and
                    ((PS2Tracks.TrackTargets.censusId eq log.characterId))
        }.toMutableList()


        val player = PS2DataCache.characterById(log.characterId) ?: return
        // if this player has an outfit, also pull targets for that outfit
        if(player.outfit != null) {
            PS2Tracks.TrackTarget.find {
                PS2Tracks.TrackTargets.type eq PS2Tracks.PS2EventType.OUTFIT and
                        (PS2Tracks.TrackTargets.censusId eq player.outfit!!.outfitId)
            }.run(targets::addAll)
        }

        targets.forEach { target ->
            val chan = getChannel(target) ?: return@forEach

            try {

                val faction = PS2Faction[player.faction]
                chan.createEmbed { spec ->
                    val outfitTag = if(player.outfit?.lastKnownTag != null) "[${player.outfit!!.lastKnownTag}] " else ""
                    spec.setAuthor("$outfitTag${player.lastKnownName} ${log.event.str}", null, faction.image)
                    spec.setColor(log.event.color)
                }.awaitSingle()

            } catch(ce: ClientException) {
                if(ce.status.code() == 403) {
                    LOG.warn("Unable to send PS2 notification to channel '${chan.id.asString()}. Disabling featurae in channel.")
                    TrackerUtil.permissionDenied(chan, FeatureChannel::ps2Channel, target::delete)
                } else throw ce
            }
        }
    }

    @WithinExposedContext
    suspend fun onContinentUpdate(update: WSSEvent.ContinentUpdate) {
        val targets = PS2Tracks.TrackTarget.find {
            PS2Tracks.TrackTargets.type eq PS2Tracks.PS2EventType.CONTINENT //and
                   // (PS2Tracks.TrackTargets.censusId eq update.server.worldIdStr) todo uncomment, for now display all
        }
        if(targets.empty()) return

        targets.forEach { target ->
            val chan = getChannel(target) ?: return@forEach

            try {

                chan.createEmbed { spec ->
                    // display server name, continent name, unlock (green) vs lock (faction color or gray(?))
                    val server = "${update.server.name}: Continent "

                    when(update.event) {
                        WSSEvent.ContinentEvent.LOCK -> {
                            // lock event: display locking faction if won by alert
                            val victor = update.lockedBy

                            spec.setAuthor("$server Locked", null, victor?.image)
                            val color = victor?.color ?: Color.GRAY
                            spec.setColor(color)

                            val faction = if(victor != null) " by the ${victor.fullName}" else ""
                            spec.setDescription("${update.zone.code} has been locked$faction.")
                        }
                        WSSEvent.ContinentEvent.UNLOCK -> {
                            spec.setAuthor("$server Unlocked", null, null)
                            spec.setColor(Color.GREEN)
                            spec.setDescription("${update.zone.code} has been unlocked.")
                        }
                    }
                }.awaitSingle()

            } catch(ce: ClientException) {
                if(ce.status.code() == 403) {
                    LOG.warn("Unable to send PS2 notification to channel '${chan.id.asString()}. Disabling featurae in channel.")
                    TrackerUtil.permissionDenied(chan, FeatureChannel::ps2Channel, target::delete)
                } else throw ce
            }
        }
    }

    @WithinExposedContext
    suspend fun onBaseCap(cap: WSSEvent.FacilityControl) {
        return
        cap.outfitId ?: return
        PS2Tracks.TrackTarget.find {
            PS2Tracks.TrackTargets.type eq PS2Tracks.PS2EventType.OUTFITCAP and
                    (PS2Tracks.TrackTargets.censusId eq cap.outfitId)
            // todo needs further filtering before sending message
        }.forEach { target ->
            val chan = getChannel(target) ?: return
        }
    }
}