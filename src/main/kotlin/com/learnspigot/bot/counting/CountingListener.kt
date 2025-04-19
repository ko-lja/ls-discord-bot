package com.learnspigot.bot.counting

import com.github.mlgpenguin.mathevaluator.Evaluator
import com.learnspigot.bot.Server
import gg.flyte.neptune.annotation.Inject
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class CountingListener : ListenerAdapter() {
    @Inject private lateinit var countingRegistry: CountingRegistry

    val currentCount: Int get() = countingRegistry.currentCount
    var lastCount: Message? = null

    private val thinking = Emoji.fromUnicode("🤔")
    private val oneHundred = Emoji.fromUnicode("💯")

    private fun Channel.isCounting() = id == Server.countingChannel.id
    private fun Message.millisSinceLastCount() = timeCreated.toInstant().toEpochMilli() - (lastCount?.timeCreated?.toInstant()?.toEpochMilli() ?: 0)

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (!isValidMessage(event)) return

        val msg = event.message.contentRaw
        val userId = event.author.id

        if (!Evaluator.isValidSyntax(msg)) return

        val evaluated = Evaluator.eval(msg).intValue()
        when {
            evaluated == currentCount + 1 -> handleValidCount(event, evaluated, userId)
            evaluated == currentCount && event.message.millisSinceLastCount() < 600 -> handleSimultaneousCount(event)
            else -> handleInvalidCount(event, evaluated)
        }
    }

    private fun isValidMessage(event: MessageReceivedEvent): Boolean {
        if (event.author.isBot || !event.isFromGuild || !event.channel.isCounting() || event.guild.id != Server.guildId) return false
        if (event.message.attachments.isNotEmpty()) return false
        return true
    }

    private fun handleValidCount(event: MessageReceivedEvent, evaluated: Int, userId: String) {
        if (userId.equals(lastCount?.author?.id, true)) {
            handleDoubleCount(event)
            return
        }

        val reactionEmoji = if (evaluated % 100 == 0) oneHundred else Server.upvoteEmoji
        lastCount = event.message
        event.message.addReaction(reactionEmoji).queue()
        countingRegistry.incrementCount(event.author)
    }

    private fun handleDoubleCount(event: MessageReceivedEvent) {
        event.message.addReaction(Server.downvoteEmoji)
        val insultMessage = CountingInsults.doubleCountInsults.random()
        event.message.reply("$insultMessage ${event.author.asMention}, The count has been reset to 1.").queue()
        fuckedUp(event.author)
    }

    private fun handleSimultaneousCount(event: MessageReceivedEvent) {
        event.message.addReaction(thinking).queue()
        event.message.reply("I'll let this one slide").queue()
    }

    private fun handleInvalidCount(event: MessageReceivedEvent, evaluated: Int) {
        val next = currentCount + 1
        fuckedUp(event.author)
        event.message.addReaction(Server.downvoteEmoji).queue()
        val insultMessage = CountingInsults.fuckedUpInsults.random()
        event.message.reply("$insultMessage ${event.author.asMention}, The next number was $next, not $evaluated.").queue()
    }

    override fun onMessageDelete(event: MessageDeleteEvent) {
        if (!event.channel.isCounting()) return
        if (event.messageId == lastCount?.id) {
            Server.countingChannel.sendMessage("${lastCount?.author?.asMention} deleted their count of $currentCount").queue()
        }
    }

    override fun onMessageUpdate(event: MessageUpdateEvent) {
        if (!event.channel.isCounting()) return
        if (event.messageId == lastCount?.id) {
            Server.countingChannel.sendMessage("${event.author.asMention} edited their count of $currentCount").queue()
        }
    }

    fun fuckedUp(user: User) {
        lastCount = null
        countingRegistry.fuckedUp(user)
    }
}
