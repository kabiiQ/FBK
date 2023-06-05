package moe.kabii.trackers.videos.kick.watcher

import discord4j.rest.util.Color
import moe.kabii.instances.DiscordInstances
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.trackers.videos.kick.api.KickParser

abstract class KickNotifier(instances: DiscordInstances) : StreamWatcher(instances) {

    companion object {
        private val liveColor = KickParser.color
        private val inactiveColor = Color.of(2116880)
    }
}