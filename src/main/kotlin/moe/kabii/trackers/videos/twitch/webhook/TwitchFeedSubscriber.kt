package moe.kabii.trackers.videos.twitch.webhook

import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.newRequestBuilder
import moe.kabii.trackers.videos.twitch.parser.TwitchParser
import okhttp3.FormBody

class TwitchFeedSubscriber {

    private val postAddress = Keys.config[Keys.Youtube.callbackAddress]
    private val postPort = Keys.config[Keys.Twitch.webhookPort]
    private val signingKey = Keys.config[Keys.Twitch.signingKey]
    private val clientId = TwitchParser.clientID

    enum class Mode(val str: String) {
        SUBSCRIBE("subscribe"),
        UNSUBSCRIBE("unsubscribe")
    }

    fun subscribe(userId: String) = request(userId, Mode.SUBSCRIBE)

    fun unsubscribe(userId: String) = request(userId, Mode.UNSUBSCRIBE)

    private fun request(userId: String, mode: Mode): String? {
        val topic = "https://api.twitch.tv/helix/streams?user_id=$userId"

        val body = FormBody.Builder()
            .add("hub.callback", "http://$postAddress:$postPort?userId=$userId")
            .add("hub.mode", mode.str)
            .add("hub.topic", topic)
            .add("hub.lease_seconds", "432000")
            .add("hub.secret", signingKey)
            .build()

        val request = newRequestBuilder()
            .url("https://api.twitch.tv/helix/webhooks/hub")
            .header("Client-ID", clientId)
            .header("Authorization", TwitchParser.oauth.authorization)
            .post(body)
            .build()

        LOG.info("Requesting $mode for Twitch feed: $topic")
        val response = OkHTTP.newCall(request).execute()
        return response.use { rs ->
            LOG.debug("${rs.code} :: ${rs.body!!.string()}")
            if(response.isSuccessful) topic else null
        }
    }
}