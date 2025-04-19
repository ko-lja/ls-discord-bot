package com.learnspigot.bot.profile

import com.learnspigot.bot.reputation.Reputation
import com.learnspigot.bot.util.Mongo
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import org.bson.Document
import java.util.*

class ProfileRegistry {
    val profileCache: MutableMap<String, Profile> = TreeMap(String.CASE_INSENSITIVE_ORDER)
    private val urlProfiles: MutableMap<String, Profile> = TreeMap()
    val contributorSelectorCache: MutableMap<String, List<String>> = HashMap()
    val messagesToRemove: MutableMap<String, Message> = HashMap()

    init {
        Mongo.userCollection.find().forEach { document ->
            createProfileFromDocument(document)?.let { profile ->
                profileCache[profile.id] = profile
                profile.udemyProfileUrl?.let { url -> urlProfiles[url] = profile }
            }
        }
    }

    private fun createProfileFromDocument(document: Document): Profile? {
        val reputation = TreeMap<Int, Reputation>()
        document.get("reputation", Document::class.java)?.forEach { id, rep ->
            (rep as? Document)?.let { repDoc ->
                reputation[id.toInt()] = Reputation(
                    convertToLongTimestamp(repDoc["timestamp"]!!),
                    repDoc.getString("fromMemberId"),
                    repDoc.getString("fromPostId")
                )
            }
        }

        return Profile(
            document.getString("_id") ?: return null,
            document.getString("tag"),
            document.getString("udemyProfileUrl"),
            reputation,
            document.getBoolean("notifyOnRep", true),
            document.getBoolean("intellijKeyGiven", false),
            document.getInteger("highestCount", 0),
            document.getInteger("totalCounts", 0),
            document.getInteger("countingFuckUps", 0)
        )
    }

    private fun convertToLongTimestamp(timestamp: Any): Long = when (timestamp) {
        is Int -> timestamp.toLong()
        is Long -> timestamp
        is String -> timestamp.toLongOrNull() ?: throw IllegalArgumentException("Invalid timestamp format")
        else -> throw IllegalArgumentException("Unsupported timestamp format")
    }

    fun findById(id: String): Profile? = profileCache[id]

    fun findByUser(user: User): Profile = findById(user.id) ?: createNewProfile(user)

    private fun createNewProfile(user: User): Profile {
        return Profile(
            user.id,
            user.name,
            null,
            TreeMap(),
            true,
            false,
            0,
            0,
            0
        ).apply {
            profileCache[user.id] = this
            save()
        }
    }

    fun findByURL(udemyURL: String): Profile? = urlProfiles[udemyURL]
}