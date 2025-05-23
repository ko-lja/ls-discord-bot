package com.learnspigot.bot.profile

import com.learnspigot.bot.Server
import com.learnspigot.bot.util.Mongo
import com.learnspigot.bot.util.embed
import com.mongodb.client.model.Filters
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.ErrorResponse

class ProfileListener : ListenerAdapter() {
    override fun onGuildMemberJoin(e: GuildMemberJoinEvent) {
        if (e.guild.id != Server.guildId) return

        val document = Mongo.userCollection.find(Filters.eq("_id", e.user.id)).first()
        if (document?.containsKey("udemyProfileUrl") == true) {
            handleReturningUser(e)
        } else {
            handleNewUser(e)
        }
    }

    private fun handleReturningUser(e: GuildMemberJoinEvent) {
        sendWelcomeMessage(e.user, """
            You have already verified previously so your Student role has been restored.
            
            *PS: Use [our pastebin](https://paste.learnspigot.com) - pastes never expire!*
        """.trimIndent())
        e.guild.addRoleToMember(e.user, e.guild.getRoleById(Server.studentRole.id)!!).queue()
    }

    private fun handleNewUser(e: GuildMemberJoinEvent) {
        sendWelcomeMessage(e.user, """
            You have joined the exclusive support community for the [Develop Minecraft Plugins (Java)](https://learnspigot.com) Udemy course.
            
            :question: Don't have the course? Grab it at <https://learnspigot.com>
            
            :thinking: Not convinced? Take a look at what everyone else has to say at <https://vouches.learnspigot.com>
            
            :star: Have it? Follow the instructions in ${Server.verifyChannel.asMention}
            
            *PS: Use [our pastebin](https://paste.learnspigot.com) - pastes never expire!*
        """.trimIndent(), "Without verifying, you can still read the server but won't have access to our 24/7 support team and dozens of tutorials and projects.")
    }

    private fun sendWelcomeMessage(user: net.dv8tion.jda.api.entities.User, description: String, footer: String? = null) {
        user.openPrivateChannel().complete().let { channel ->
            channel.sendMessageEmbeds(
                embed()
                    .setTitle("Welcome to the Discord! :tada:")
                    .setDescription(description)
                    .apply { footer?.let { setFooter(it) } }
                    .build()
            ).queue(null, ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER) {})
        }
    }
}