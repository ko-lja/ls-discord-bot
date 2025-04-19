package com.learnspigot.bot.counting

import com.learnspigot.bot.profile.ProfileRegistry
import com.learnspigot.bot.util.embed
import gg.flyte.neptune.annotation.Command
import gg.flyte.neptune.annotation.Description
import gg.flyte.neptune.annotation.Inject
import gg.flyte.neptune.annotation.Optional
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class CountingCommand {
    @Inject private lateinit var profileRegistry: ProfileRegistry
    @Inject private lateinit var countingRegistry: CountingRegistry

    @Command(name = "countingstats", description = "View counting statistics")
    fun onCountingCommand(
        event: SlashCommandInteractionEvent,
        @Description("User's stats to view") @Optional user: User?
    ) {
        event.replyEmbeds(
            if (user == null) createServerStatsEmbed() else createUserStatsEmbed(user)
        ).setEphemeral(true).queue()
    }

    private fun createServerStatsEmbed() = embed()
        .setTitle("Server counting statistics")
        .setDescription("""
            - Last Count: ${countingRegistry.currentCount}
            - Total Counts: ${countingRegistry.serverTotalCounts}
            - Highest Count: ${countingRegistry.topServerCount}
        """.trimIndent())
        .addField(
            "Top 5 counters",
            countingRegistry.getTop5().joinToString("") { profile ->
                "\n- <@${profile.id}>: ${profile.totalCounts}"
            },
            false
        )
        .build()

    private fun createUserStatsEmbed(user: User) = embed()
        .setTitle("${user.name}'s counting statistics")
        .setDescription("""
            - Total Counts: ${profileRegistry.findByUser(user).totalCounts}
            - Highest Count: ${profileRegistry.findByUser(user).highestCount}
            - Mistakes: ${profileRegistry.findByUser(user).countingFuckUps}
        """.trimIndent())
        .build()
}