package com.fsck.k9.message

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fsck.k9.helper.ReplyToParser
import com.fsck.k9.mail.testing.message.buildMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.preference.interaction.InteractionSettings
import net.thunderbird.core.preference.interaction.InteractionSettingsPreferenceManager
import net.thunderbird.core.preference.interaction.ReplyButtonAction
import org.junit.Test

private const val IDENTITY_EMAIL_ADDRESS = "myself@domain.example"

class LegacyReplyActionStrategyTest {
    private val account = createAccount()
    private val fakeInteractionPreferences = FakeInteractionSettingsPreferenceManager()
    private val replyActionStrategy = LegacyReplyActionStrategy(ReplyToParser(), fakeInteractionPreferences)

    @Test
    fun `message sent to only our identity`() {
        val message = buildMessage {
            header("From", "sender@domain.example")
            header("To", IDENTITY_EMAIL_ADDRESS)
        }

        val replyActions = replyActionStrategy.getReplyActions(account, message)

        assertThat(replyActions.defaultAction).isEqualTo(ReplyAction.REPLY)
        assertThat(replyActions.additionalActions).isEmpty()
    }

    @Test
    fun `message sent to our identity and others`() {
        val message = buildMessage {
            header("From", "sender@domain.example")
            header("To", "$IDENTITY_EMAIL_ADDRESS, other@domain.example")
        }

        val replyActions = replyActionStrategy.getReplyActions(account, message)

        assertThat(replyActions.defaultAction).isEqualTo(ReplyAction.REPLY)
        assertThat(replyActions.additionalActions).containsExactly(ReplyAction.REPLY_ALL)
    }

    @Test
    fun `message sent to our identity and others (CC)`() {
        val message = buildMessage {
            header("From", "sender@domain.example")
            header("Cc", "$IDENTITY_EMAIL_ADDRESS, other@domain.example")
        }

        val replyActions = replyActionStrategy.getReplyActions(account, message)

        assertThat(replyActions.defaultAction).isEqualTo(ReplyAction.REPLY)
        assertThat(replyActions.additionalActions).containsExactly(ReplyAction.REPLY_ALL)
    }

    @Test
    fun `message sent to our identity and others (To+CC)`() {
        val message = buildMessage {
            header("From", "sender@domain.example")
            header("To", IDENTITY_EMAIL_ADDRESS)
            header("Cc", "other@domain.example")
        }

        val replyActions = replyActionStrategy.getReplyActions(account, message)

        assertThat(replyActions.defaultAction).isEqualTo(ReplyAction.REPLY)
        assertThat(replyActions.additionalActions).containsExactly(ReplyAction.REPLY_ALL)
    }

    @Test
    fun `message sent to our identity and others (CC+To)`() {
        val message = buildMessage {
            header("From", "sender@domain.example")
            header("To", "other@domain.example")
            header("Cc", IDENTITY_EMAIL_ADDRESS)
        }

        val replyActions = replyActionStrategy.getReplyActions(account, message)

        assertThat(replyActions.defaultAction).isEqualTo(ReplyAction.REPLY)
        assertThat(replyActions.additionalActions).containsExactly(ReplyAction.REPLY_ALL)
    }

    @Test
    fun `message where neither sender nor recipient addresses belong to account`() {
        val message = buildMessage {
            header("From", "sender@domain.example")
            header("To", "recipient@domain.example")
        }

        val replyActions = replyActionStrategy.getReplyActions(account, message)

        assertThat(replyActions.defaultAction).isEqualTo(ReplyAction.REPLY)
        assertThat(replyActions.additionalActions).containsExactly(ReplyAction.REPLY_ALL)
    }

    @Test
    fun `message without any sender or recipient headers`() {
        val message = buildMessage {}

        val replyActions = replyActionStrategy.getReplyActions(account, message)

        assertThat(replyActions.defaultAction).isNull()
        assertThat(replyActions.additionalActions).isEmpty()
    }

    @Test
    fun `message sent to our identity and others with reply button set to reply to sender`() {
        fakeInteractionPreferences.setReplyButtonAction(ReplyButtonAction.ReplyToSender)
        val message = buildMessage {
            header("From", "sender@domain.example")
            header("To", "$IDENTITY_EMAIL_ADDRESS, other@domain.example")
        }

        val replyActions = replyActionStrategy.getReplyActions(account, message)

        assertThat(replyActions.defaultAction).isEqualTo(ReplyAction.REPLY)
        assertThat(replyActions.additionalActions).containsExactly(ReplyAction.REPLY_ALL)
    }

    @Test
    fun `message sent to our identity and others with reply button set to reply all`() {
        fakeInteractionPreferences.setReplyButtonAction(ReplyButtonAction.ReplyAll)
        val message = buildMessage {
            header("From", "sender@domain.example")
            header("To", "$IDENTITY_EMAIL_ADDRESS, other@domain.example")
        }

        val replyActions = replyActionStrategy.getReplyActions(account, message)

        assertThat(replyActions.defaultAction).isEqualTo(ReplyAction.REPLY_ALL)
        assertThat(replyActions.additionalActions).containsExactly(ReplyAction.REPLY)
    }

    @Test
    fun `message sent to only our identity with reply button set to reply all`() {
        fakeInteractionPreferences.setReplyButtonAction(ReplyButtonAction.ReplyAll)
        val message = buildMessage {
            header("From", "sender@domain.example")
            header("To", IDENTITY_EMAIL_ADDRESS)
        }

        val replyActions = replyActionStrategy.getReplyActions(account, message)

        assertThat(replyActions.defaultAction).isEqualTo(ReplyAction.REPLY)
        assertThat(replyActions.additionalActions).isEmpty()
    }

    private fun createAccount(): LegacyAccountDto {
        return LegacyAccountDto("00000000-0000-4000-0000-000000000000").apply {
            identities += Identity(name = "Myself", email = IDENTITY_EMAIL_ADDRESS)
        }
    }

    private class FakeInteractionSettingsPreferenceManager : InteractionSettingsPreferenceManager {
        private val prefs = MutableStateFlow(InteractionSettings())

        override fun save(config: InteractionSettings) = Unit

        override fun getConfig(): InteractionSettings = prefs.value

        override fun getConfigFlow(): Flow<InteractionSettings> = prefs

        fun setReplyButtonAction(replyButtonAction: ReplyButtonAction) {
            prefs.update { it.copy(messageViewReplyButtonAction = replyButtonAction) }
        }
    }
}
