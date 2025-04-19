package com.learnspigot.bot.profile

import com.learnspigot.bot.reputation.Reputation
import com.learnspigot.bot.util.Mongo
import com.learnspigot.bot.util.embed
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.requests.ErrorResponse
import org.bson.Document
import java.time.Instant
import java.util.*

data class Profile(
    val id: String,
    val tag: String?,
    var udemyProfileUrl: String?,
    val reputation: NavigableMap<Int, Reputation>,
    val notifyOnRep: Boolean,
    var intellijKeyGiven: Boolean,
    var highestCount: Int,
    var totalCounts: Int,
    var countingFuckUps: Int
) {

    fun addReputation(user: User, fromUserId: String, fromPostId: String, amount: Int) {
        val startKey = if (reputation.isEmpty()) 0 else reputation.lastKey() + 1
        (startKey until startKey + amount).forEach { key ->
            reputation[key] = Reputation(Instant.now().epochSecond, fromUserId, fromPostId)
        }
        save()

        user.openPrivateChannel().complete().let { channel ->
            channel.sendMessageEmbeds(
                embed()
                    .setAuthor("You have ${reputation.size} reputation in total")
                    .setTitle("You earned ${if (amount == 1) "" else "$amount "}reputation")
                    .setDescription("You gained reputation from <@$fromUserId> in <#$fromPostId>.")
                    .build()
            ).queue(null, ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER) {})
        }
    }

    fun removeReputation(startId: Int, endId: Int) {
        (startId..endId).forEach { reputation.remove(it) }
        save()
    }

    fun save() {
        Mongo.userCollection.replaceOne(
            Filters.eq("_id", id),
            Document().apply {
                put("_id", id)
                put("tag", tag)
                put("udemyProfileUrl", udemyProfileUrl)
                put("reputation", Document().apply {
                    reputation.forEach { (id, rep) -> put(id.toString(), rep.document()) }
                })
                put("notifyOnRep", notifyOnRep)
                put("intellijKeyGiven", intellijKeyGiven)
                put("highestCount", highestCount)
                put("totalCounts", totalCounts)
                put("countingFuckUps", countingFuckUps)
            },
            ReplaceOptions().upsert(true)
        )
    }

    fun incrementCount(currentCount: Int) {
        totalCounts++
        if (currentCount > highestCount) highestCount = currentCount
        saveCounting()
    }

    fun fuckedUpCounting() {
        countingFuckUps++
        saveCounting()
    }

    private fun saveCounting() {
        Mongo.userCollection.find(Filters.eq("_id", id)).first()?.let { doc ->
            doc.apply {
                put("highestCount", highestCount)
                put("totalCounts", totalCounts)
                put("countingFuckUps", countingFuckUps)
            }
            Mongo.userCollection.replaceOne(Filters.eq("_id", id), doc, ReplaceOptions().upsert(true))
        }
    }

}
