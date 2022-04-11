package moe.kabii.command.registration

import discord4j.common.JacksonResources
import discord4j.discordjson.json.ApplicationCommandRequest
import java.io.File

interface CommandRegistrar {
    /*
    many commands (including global chat, guild chat, message user) are generated from files of their raw Discord request form
    these all use the same json format
     */
    fun loadFileCommands(root: File): List<ApplicationCommandRequest> {
        val mapper = JacksonResources.create().objectMapper
        return searchConfigFileTree(root).map { f ->
            mapper.readValue(f.readText(), ApplicationCommandRequest::class.java)
        }
    }

    // recursively build configuration file list
    private fun searchConfigFileTree(root: File): List<File> {
        val (directory, config) = root.listFiles()!!.partition { f -> f.isDirectory }
        return config.filter { f -> f.extension == "json" } + directory.flatMap(::searchConfigFileTree)
    }
}