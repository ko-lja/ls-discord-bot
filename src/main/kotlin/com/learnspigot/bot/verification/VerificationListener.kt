package com.learnspigot.bot.verification

import com.learnspigot.bot.Environment
import com.learnspigot.bot.profile.ProfileRegistry
import com.learnspigot.bot.util.Mongo
import com.learnspigot.bot.util.embed
import com.mongodb.client.model.Filters
import gg.flyte.neptune.annotation.Inject
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionType
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.requests.ErrorResponse
import java.util.regex.Pattern

class VerificationListener : ListenerAdapter() {

    @Inject
    private lateinit var profileRegistry: ProfileRegistry

    override fun onButtonInteraction(e: ButtonInteractionEvent) {
        if (e.button.id == null) return

        when {
            e.button.id == "verify" -> handleVerifyButton(e)
            e.button.id!!.startsWith("v|") -> handleVerificationAction(e)
        }
    }

    private fun handleVerifyButton(e: ButtonInteractionEvent) {
        if (e.member!!.roles.contains(e.jda.getRoleById(Environment["STUDENT_ROLE_ID"]))) {
            e.reply("You're already a student!").setEphemeral(true).queue()
            return
        }

        val verifyModal = createVerificationModal()
        e.replyModal(verifyModal).queue()
    }

    private fun createVerificationModal() = Modal.create("verify", "Verify Your Profile")
        .addActionRows(
            ActionRow.of(
                TextInput.create("url", "Udemy Profile URL", TextInputStyle.SHORT)
                    .setPlaceholder("https://www.udemy.com/user/example")
                    .setMinLength(10)
                    .setMaxLength(70)
                    .setRequired(true)
                    .build()
            ),
            ActionRow.of(
                TextInput.create("personal_plan", "On Personal/Business Subscription?", TextInputStyle.SHORT)
                    .setPlaceholder("Yes/No - If you purchased the course directly, answer No")
                    .setMinLength(2)
                    .setMaxLength(3)
                    .setRequired(false)
                    .build()
            )
        )
        .build()

    private fun handleVerificationAction(e: ButtonInteractionEvent) {
        val info = e.button.id!!.split("|")
        val action = info[1]
        val guild = e.guild!!

        if (!hasVerificationPermission(e)) {
            e.reply("Sorry, you can't verify student profiles.").setEphemeral(true).queue()
            return
        }

        val url = info[2]
        val member = guild.getMemberById(info[3]) ?: return
        val questionChannel = guild.getTextChannelById(Environment["QUESTIONS_CHANNEL_ID"])

        when (action) {
            "a" -> handleApproval(e, member, url, questionChannel)
            "wl" -> handleWrongLink(e, member, questionChannel)
            "ch" -> handleCoursesHidden(e, member, questionChannel)
            "no" -> handleNotOwned(e, member, questionChannel)
            "u" -> handleUndo(e, info, member, url, questionChannel)
        }
    }

    private fun hasVerificationPermission(e: ButtonInteractionEvent): Boolean {
        val allowedRoles = listOf(
            Environment["SUPPORT_ROLE_ID"],
            Environment["STAFF_ROLE_ID"],
            Environment["MANAGEMENT_ROLE_ID"],
            Environment["VERIFIER_ROLE_ID"]
        )
        val memberRoles = e.member!!.roles.map { it.id }
        return allowedRoles.any { it in memberRoles }
    }

    private fun handleApproval(e: ButtonInteractionEvent, member: net.dv8tion.jda.api.entities.Member, url: String, questionChannel: net.dv8tion.jda.api.entities.channel.concrete.TextChannel?) {
        val guild = e.guild!!
        guild.addRoleToMember(member, guild.getRoleById(Environment["STUDENT_ROLE_ID"])!!).queue()

        guild.getTextChannelById(Environment["GENERAL_CHANNEL_ID"])!!.sendMessageEmbeds(
            embed()
                .setTitle("Welcome")
                .setDescription("Please welcome ${member.asMention} as a new Student! :heart:").build()
        ).queue()

        sendPrivateMessage(member, "Your profile was approved! Go ahead and enjoy our community :heart:")

        profileRegistry.findByUser(member.user).let {
            it.udemyProfileUrl = url
            it.save()
        }

        updateVerificationMessage(e, member, "has approved :mention:'s profile")
    }

    private fun handleWrongLink(e: ButtonInteractionEvent, member: net.dv8tion.jda.api.entities.Member, questionChannel: net.dv8tion.jda.api.entities.channel.concrete.TextChannel?) {
        questionChannel!!.sendMessage(member.asMention).setEmbeds(
            embed()
                .setTitle("Profile Verification")
                .setDescription(
                    """
                    Staff looked at your profile and found that you have sent the wrong profile link!
                    
                    The URL you need to use is the link to your public profile, to get this:
                    :one: Hover over your profile picture in the top right on Udemy
                    :two: Select "Public profile" from the dropdown menu
                    :three: Copy the link from your browser
                    """
                )
                .build()
        ).queue()

        updateVerificationMessage(e, member, "hasn't approved :mention:, as they specified an invalid link")
    }

    private fun handleCoursesHidden(e: ButtonInteractionEvent, member: net.dv8tion.jda.api.entities.Member, questionChannel: net.dv8tion.jda.api.entities.channel.concrete.TextChannel?) {
        questionChannel!!.sendMessage(member.asMention).setEmbeds(
            embed()
                .setTitle("Profile Verification")
                .setDescription(
                    """
                    Staff looked at your profile and found that you have privacy settings disabled which means we can't see your courses.
                    
                    Change here: <https://www.udemy.com/instructor/profile/privacy/>
                    
                    Enable "Show courses you're taking on your profile page" and verify again!
                    """
                )
                .build()
        ).queue()

        updateVerificationMessage(e, member, "hasn't approved :mention:, as they're unable to view their courses")
    }

    private fun handleNotOwned(e: ButtonInteractionEvent, member: net.dv8tion.jda.api.entities.Member, questionChannel: net.dv8tion.jda.api.entities.channel.concrete.TextChannel?) {
        questionChannel!!.sendMessage(member.asMention).setEmbeds(
            embed()
                .setTitle("Profile Verification")
                .setDescription("Staff looked at your profile and found that you do not own the course. If you have purchased the course, please make sure it's visible on your public profile.")
                .build()
        ).queue()

        updateVerificationMessage(e, member, "hasn't approved :mention:, as they do not own the course")
    }

    private fun handleUndo(e: ButtonInteractionEvent, info: List<String>, member: net.dv8tion.jda.api.entities.Member, url: String, questionChannel: net.dv8tion.jda.api.entities.channel.concrete.TextChannel?) {
        val originalActionTaker = info[4]
        if (e.member!!.id != originalActionTaker && !e.member!!.roles.contains(e.guild!!.getRoleById(Environment["MANAGEMENT_ROLE_ID"])!!)) {
            e.reply("Sorry, you can't undo that verification decision.").setEphemeral(true).queue()
            return
        }

        val guild = e.guild!!
        guild.removeRoleFromMember(member, guild.getRoleById(Environment["STUDENT_ROLE_ID"])!!).queue()

        e.message.editMessageEmbeds(
            embed()
                .setTitle("Profile Verification")
                .setDescription(
                    "Please verify that ${member.asMention} owns the course.\n\nPrevious action reverted by: ${e.member!!.asMention}"
                )
                .addField("Udemy Link", url, false)
                .build()
        )
            .setActionRow(
                Button.success("v|a|$url|${member.id}", "Approve"),
                Button.danger("v|wl|$url|${member.id}", "Wrong Link"),
                Button.danger("v|ch|$url|${member.id}", "Courses Hidden"),
                Button.danger("v|no|$url|${member.id}", "Not Owned")
            )
            .queue()

        e.interaction.deferEdit().queue()

        questionChannel!!.sendMessage(member.asMention).setEmbeds(
            embed()
                .setTitle("Profile Verification")
                .setDescription(
                    """
                    Please disregard the previous message regarding your verification status - a staff member has reverted the action. Please remain patient while waiting for a corrected decision.
                    
                    If you were previously verified and granted the Student role, the role has been removed pending the corrected decision from staff.
                    """
                )
                .build()
        ).queue()
    }

    private fun updateVerificationMessage(e: ButtonInteractionEvent, member: net.dv8tion.jda.api.entities.Member, description: String) {
        e.message.editMessageEmbeds(
            embed()
                .setTitle("Profile Verification")
                .setDescription(
                    "${e.member!!.asMention} ${description.replace(":mention:", member.asMention)}."
                )
                .build()
        )
            .setActionRow(
                Button.danger("v|u|${e.button.id!!.split("|")[2]}|${member.id}|${e.member!!.id}", "Undo")
            )
            .queue()

        e.interaction.deferEdit().queue()
    }

    private fun sendPrivateMessage(member: net.dv8tion.jda.api.entities.Member, message: String) {
        member.user.openPrivateChannel().queue({ channel ->
            channel.sendMessageEmbeds(
                embed()
                    .setTitle("Profile Verification")
                    .setDescription(message)
                    .setFooter("PS: Want your free 6 months IntelliJ Ultimate key? Run /getkey in the Discord server!")
                    .build()
            ).queue(null, ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER) {})
        }, null)
    }

    override fun onModalInteraction(e: ModalInteractionEvent) {
        if (e.interaction.type != InteractionType.MODAL_SUBMIT || e.modalId != "verify") return

        var url = e.getValue("url")!!.asString
        val isPersonalPlan = e.getValue("personal_plan")?.asString?.lowercase() == "yes"

        if (url.contains("|") || url.startsWith("https://www.udemy.com/course")) {
            e.reply("Invalid profile link.").setEphemeral(true).queue()
            return
        }

        if (e.member!!.roles.contains(e.jda.getRoleById(Environment["STUDENT_ROLE_ID"]))) {
            e.reply("You're already a Student!").setEphemeral(true).queue()
            return
        }

        if (url.endsWith("/")) {
            url = url.substring(0, url.length - 1)
        }

        if (Mongo.userCollection.countDocuments(
                Filters.eq(
                    "udemyProfileUrl",
                    Pattern.compile(url, Pattern.CASE_INSENSITIVE)
                )
            ) > 0
        ) {
            e.reply("This profile has already been verified.").setEphemeral(true).queue()
            return
        }

        val embed = embed()
            .setTitle("Profile Verification")
            .setDescription("Please verify that ${e.member!!.asMention} owns the course.")
            .addField("Udemy Link", url, false)
            .build()

        val buttons = listOf(
            Button.success("v|a|$url|${e.member!!.id}", "Approve"),
            Button.danger("v|wl|$url|${e.member!!.id}", "Wrong Link"),
            Button.danger("v|ch|$url|${e.member!!.id}", "Courses Hidden"),
            Button.danger("v|no|$url|${e.member!!.id}", "Not Owned")
        )

        e.jda.getTextChannelById(Environment["VERIFY_CHANNEL_ID"])!!.sendMessageEmbeds(embed)
            .setActionRow(buttons)
            .queue()

        e.reply("Your verification request has been sent to the staff team.").setEphemeral(true).queue()
    }
}