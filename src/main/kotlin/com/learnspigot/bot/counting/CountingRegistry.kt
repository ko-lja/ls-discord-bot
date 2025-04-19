package com.learnspigot.bot.counting

import com.learnspigot.bot.Bot
import com.learnspigot.bot.profile.Profile
import com.learnspigot.bot.util.Mongo
import com.mongodb.client.model.Filters
import net.dv8tion.jda.api.entities.User
import org.bson.Document

class CountingRegistry(private val bot: Bot) {
    private val profileRegistry get() = bot.profileRegistry()
    private val mongoCollection = Mongo.countingCollection

    var topServerCount = 0
    var serverTotalCounts = 0
    var currentCount = 0

    init {
        mongoCollection.find().first()?.let { doc ->
            topServerCount = doc.getInteger("highestCount", 0)
            currentCount = doc.getInteger("currentCount", 0)
            serverTotalCounts = doc.getInteger("serverTotalCounts", 0)
        } ?: run {
            mongoCollection.insertOne(Document().apply {
                put("highestCount", 0)
                put("currentCount", 0)
                put("serverTotalCounts", 0)
            })
        }
    }

    fun getTop5(): List<Profile> = profileRegistry.profileCache.values
        .filter { it.totalCounts > 0 }
        .sortedByDescending { it.totalCounts }
        .take(5)

    fun incrementCount(user: User) {
        currentCount++
        serverTotalCounts++
        if (currentCount > topServerCount) topServerCount = currentCount
        
        profileRegistry.findByUser(user).incrementCount(currentCount)
        
        mongoCollection.find().first()?.let { doc ->
            doc.apply {
                put("highestCount", topServerCount)
                put("currentCount", currentCount)
                put("serverTotalCounts", serverTotalCounts)
            }
            mongoCollection.replaceOne(Filters.eq("_id", doc.getObjectId("_id")), doc)
        }
    }

    fun fuckedUp(user: User) {
        currentCount = 0
        profileRegistry.findByUser(user).fuckedUpCounting()
    }
}