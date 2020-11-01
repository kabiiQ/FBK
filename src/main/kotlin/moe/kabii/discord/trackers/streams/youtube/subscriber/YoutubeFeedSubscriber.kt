package moe.kabii.discord.trackers.streams.youtube.subscriber

import moe.kabii.OkHTTP
import moe.kabii.data.Keys
import okhttp3.FormBody
import okhttp3.Request

class YoutubeFeedSubscriber {

    private val callbackAddress = Keys.config[Keys.Youtube.callbackAddress]
    private val callbackPort = Keys.config[Keys.Youtube.callbackPort]
    private val signingKey = Keys.config[Keys.Youtube.signingKey]

    enum class Mode(val str: String) {
        SUBSCRIBE("subscribe"),
        UNSUBSCRIBE("unsubscribe")
    }

    fun subscribe(channelId: String) = request(channelId, Mode.SUBSCRIBE)

    fun unsubscribe(channelId: String) = request(channelId, Mode.UNSUBSCRIBE)

    private fun request(channelId: String,  mode: Mode): String {
        val topic = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId"

        val body = FormBody.Builder()
            .add("hub.mode", mode.str)
            .add("hub.topic", topic)
            .add("hub.callback", "$callbackAddress:$callbackPort?channel=$channelId")
            .add("hub.secret", signingKey)
            .build()

        val request = Request.Builder()
            .url("https://pubsubhubbub.appspot.com")
            .post(body)
            .build()

        OkHTTP.newCall(request).execute()
        return topic
    }
}