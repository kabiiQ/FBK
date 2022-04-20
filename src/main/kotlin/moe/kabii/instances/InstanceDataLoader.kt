package moe.kabii.instances

import com.squareup.moshi.JsonClass
import moe.kabii.MOSHI
import java.io.File

object InstanceDataLoader {
    val instanceDir = File("files/instances/")

    val instanceAdapter = MOSHI.adapter(Instance::class.java)
    @JsonClass(generateAdapter = true)
    data class Instance(val id: Int, val token: String)

    fun loadFromFile(): List<Instance> = instanceDir.listFiles()!!
        .map(File::readText)
        .map(instanceAdapter::fromJson)
        .map(::requireNotNull)
}