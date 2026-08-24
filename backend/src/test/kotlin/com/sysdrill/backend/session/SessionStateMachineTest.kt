package com.sysdrill.backend.session

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test

class SessionStateMachineTest {

    @Test
    fun `allows the documented happy-path transitions`() {
        assertThat(SessionStateMachine.canTransition(SessionStatus.IN_PROGRESS, SessionStatus.SUBMITTED)).isTrue()
        assertThat(SessionStateMachine.canTransition(SessionStatus.SUBMITTED, SessionStatus.EVALUATING)).isTrue()
        assertThat(SessionStateMachine.canTransition(SessionStatus.EVALUATING, SessionStatus.FEEDBACK_READY)).isTrue()
        assertThat(SessionStateMachine.canTransition(SessionStatus.EVALUATING, SessionStatus.EVALUATION_FAILED)).isTrue()
        assertThat(SessionStateMachine.canTransition(SessionStatus.FEEDBACK_READY, SessionStatus.IN_PROGRESS)).isTrue()
        assertThat(SessionStateMachine.canTransition(SessionStatus.FEEDBACK_READY, SessionStatus.COMPLETED)).isTrue()
        assertThat(SessionStateMachine.canTransition(SessionStatus.EVALUATION_FAILED, SessionStatus.EVALUATING)).isTrue()
    }

    @Test
    fun `rejects skipping straight from IN_PROGRESS to COMPLETED`() {
        assertThat(SessionStateMachine.canTransition(SessionStatus.IN_PROGRESS, SessionStatus.COMPLETED)).isFalse()
    }

    @Test
    fun `rejects re-submitting a session that is already SUBMITTED`() {
        assertThat(SessionStateMachine.canTransition(SessionStatus.SUBMITTED, SessionStatus.SUBMITTED)).isFalse()
    }

    @Test
    fun `COMPLETED is terminal`() {
        assertThat(SessionStateMachine.canTransition(SessionStatus.COMPLETED, SessionStatus.IN_PROGRESS)).isFalse()
    }

    @Test
    fun `requireTransition throws for an invalid move`() {
        assertThatIllegalStateException()
            .isThrownBy { SessionStateMachine.requireTransition(SessionStatus.IN_PROGRESS, SessionStatus.EVALUATING) }
            .withMessageContaining("IN_PROGRESS")
            .withMessageContaining("EVALUATING")
    }
}
