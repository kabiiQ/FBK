package moe.kabii.instances

import com.squareup.moshi.JsonClass
import moe.kabii.MOSHI
import java.io.File

object InstanceDataLoader {
    val instanceDir = File("files/instances/")

    @JsonClass(generateAdapter = true)
    data class Instance(
        val id: Int,
        val token: String,
        val messageContent: Boolean = true,
        val presences: Boolean = true,
        val musicEnabled: Boolean = true,
        val starboardEnabled: Boolean = true
    )
    private val instanceAdapter = MOSHI.adapter(Instance::class.java)

    fun loadFromFile(): List<Instance> = instanceDir.listFiles()!!
        .map(File::readText)
        .map(instanceAdapter::fromJson)
        .map(::requireNotNull)
}