package moe.kabii.ps2.store

import discord4j.rest.util.Color
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.kabii.LOG
import moe.kabii.ps2.polling.PS2Parser
import moe.kabii.ps2.polling.json.PS2Facility
import moe.kabii.ps2.polling.json.PS2FisuPopulation
import moe.kabii.ps2.polling.json.PS2Server
import moe.kabii.ps2.polling.json.PS2Zone
import moe.kabii.util.EmojiCharacters
import kotlin.reflect.KProperty1

/*
    Data stored for the lifetime of the bot - rarely or never updates
 */
object PS2StaticData {
    val requesting  = Mutex()

    private var zones: List<PS2Zone>? = null
    private var servers: List<PS2Server>? = null
    private var facilities: List<PS2Facility>? = null

    suspend fun getZones() = requesting.withLock {
        if(zones != null) zones else {
            LOG.info("Requesting Zone data from census")
            PS2Parser.getZones().ifEmpty { null }.apply {
                LOG.debug("Zones returned: $this")
                zones = this
            }
        }
    }

    suspend fun getServerNames() = requesting.withLock {
        if(servers != null) servers else {
            LOG.info("Requesting server data from census")
            PS2Parser.getServers().ifEmpty { null }.apply {
                LOG.debug("Servers returned: $this")
                servers = this
            }
        }
    }

    suspend fun getFacilities() = requesting.withLock {
        if(facilities != null) facilities else {
            LOG.info("Requesting facility data from census")
            PS2Parser.getFacilities().ifEmpty { null }.apply {
                LOG.debug("Facilities returned: $this")
                facilities = this
            }
        }
    }
}

enum class PS2Faction(
    val apiId: Int,
    val fullName: String,
    val image: String?,
    val tag: String,
    val color: Color,
    val emoji: String,
    val populationMapping: KProperty1<PS2FisuPopulation, Int>
) {
    VANU(1, "Vanu Sovereignty", "${PS2Parser.iconRoot}/94.png", "VS", Color.of(9699583), EmojiCharacters.PS2.vs, PS2FisuPopulation::vs),
    NC(2, "New Conglomerate", "${PS2Parser.iconRoot}/12.png", "NC", Color.BLUE, EmojiCharacters.PS2.nc, PS2FisuPopulation::nc),
    TR(3, "Terran Republic", "${PS2Parser.iconRoot}/18.png", "TR", Color.RED, EmojiCharacters.PS2.tr, PS2FisuPopulation::tr),
    NSO(4, "NS Operatives", null, "NSO", Color.of(3483468), EmojiCharacters.PS2.ns, PS2FisuPopulation::ns);

    companion object {
        operator fun get(apiId: Int) = values().first { it.apiId == apiId }
    }
}

