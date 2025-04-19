package com.learnspigot.bot.profile

import com.learnspigot.bot.Bot
import com.learnspigot.bot.util.embed
import gg.flyte.neptune.annotation.Command
import gg.flyte.neptune.annotation.Description
import gg.flyte.neptune.annotation.Inject
import gg.flyte.neptune.annotation.Optional
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class ProfileCommand {
    @Inject private lateinit var profileRegistry: ProfileRegistry

    @Command(
        name = "profile",
        description = "View a user's profile",
        permissions = [Permission.MANAGE_ROLES]
    )
    fun onProfileCommand(
        event: SlashCommandInteractionEvent,
        @Description("User to show profile") @Optional user: User?,
        @Description("URL to show profile") @Optional url: String?
    ) {
        event.replyEmbeds(createProfileEmbed(user, url)).setEphemeral(true).queue()
    }

    private fun createProfileEmbed(user: User?, url: String?): MessageEmbed {
        val profileByURL = profileRegistry.findByURL(url ?: "")

        return when {
            user == null && url == null -> createUserProfileEmbed(event.user)
            user != null && url != null -> createErrorEmbed("Please choose", "Please make a choice whether you want to use a user or use a URL.")
            profileByURL != null -> createUserProfileEmbed(Bot.jda.getUserById(profileByURL.id)!!)
            user != null -> createUserProfileEmbed(user)
            else -> createErrorEmbed("Something went wrong", "Something went wrong while trying to find a profile matching your query. Please contact a manager (or higher) to look at this issue.")
        }
    }

    private fun createUserProfileEmbed(user: User): MessageEmbed {
        val profile = profileRegistry.findByUser(user)
        return embed()
            .setTitle("Profile Lookup")
            .addField("Discord", "${user.name} (${user.asMention})", false)
            .addField("Udemy", profile.udemyProfileUrl ?: "Not linked", false)
            .addField("Reputation", profile.reputation.size.toString(), true)
            .addField("(Notifications)", profile.notifyOnRep.toString(), true)
            .setThumbnail(user.effectiveAvatarUrl)
            .build()
    }

    private fun createErrorEmbed(title: String, description: String): MessageEmbed = embed()
        .setTitle("Profile Lookup")
        .addField(title, description, false)
        .build()
}