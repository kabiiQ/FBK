package moe.kabii.data.mongodb

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress
import com.mongodb.client.model.UpdateOptions
import discord4j.core.`object`.entity.Message
import moe.kabii.data.Keys
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.util.KMongoConfiguration

val upsert: UpdateOptions = UpdateOptions().upsert(true)

object MongoDBConnection {
    //private val mongoClient: CoroutineClient = KMongo.createClient().coroutine
    private val mongoClient: CoroutineClient
    val mongoDB: CoroutineDatabase

    init {
        KMongoConfiguration.registerBsonModule(JavaTimeModule())

        val username = Keys.config[Keys.MongoDB.username]
        val clientSettings = MongoClientSettings
            .builder()
            .applyToClusterSettings { cluster ->
                cluster.hosts(listOf(ServerAddress(
                    Keys.config[Keys.MongoDB.address], Keys.config[Keys.MongoDB.port]
                )))
            }
        if(username.isNotEmpty()) { // if local testing environment requires no auth this is skipped
            MongoCredential
                .createCredential(username, Keys.config[Keys.MongoDB.authDB], Keys.config[Keys.MongoDB.password].toCharArray())
                .run(clientSettings::credential)
        }
        mongoClient = KMongo.createClient(clientSettings.build()).coroutine
        mongoDB = mongoClient.getDatabase(Keys.config[Keys.MongoDB.botDB])
    }
}

data class MessageInfo(val channelID: Long, val messageID: Long) {
    companion object {
        fun of(message: Message) = MessageInfo(message.channelId.asLong(), message.id.asLong())
    }
}