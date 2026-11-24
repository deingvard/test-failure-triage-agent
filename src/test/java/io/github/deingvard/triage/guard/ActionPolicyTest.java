package io.github.deingvard.triage.guard;

import io.github.deingvard.triage.model.Action;
import io.github.deingvard.triage.model.Category;
import io.github.deingvard.triage.model.Decision;
import io.github.deingvard.triage.model.FailureCase;
import io.github.deingvard.triage.model.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static io.github.deingvard.triage.TestCases.failure;
import static org.assertj.core.api.Assertions.assertThat;

class ActionPolicyTest {

    private final ActionPolicy policy = new ActionPolicy(0.6);

    private static Verdict verdict(Category c, double confidence, Action a) {
        return new Verdict(c, confidence, a, List.of("test"), "test");
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    @DisplayName("a product bug is never re-run or quarantined, whatever the classifier suggests")
    void productBugIsNeverSilenced(Action suggested) {
        FailureCase f = failure("AssertionError", "expected 1 but was 2", "", "PFPFPFPF");

        Decision d = policy.apply(f, verdict(Category.PRODUCT_BUG, 0.95, suggested));

        assertThat(d.action()).isNotIn(ActionPolicy.SILENCING_ACTIONS);
    }

    @Test
    @DisplayName("low confidence is escalated to a human")
    void lowConfidenceEscalates() {
        Decision d = policy.apply(failure("X", "", "", ""), verdict(Category.ENVIRONMENT, 0.59, Action.NOTIFY_INFRA));

        assertThat(d.action()).isEqualTo(Action.ESCALATE);
        assertThat(d.policyNotes()).anyMatch(n -> n.contains("below"));
    }

    @Test
    @DisplayName("an UNKNOWN verdict is always escalated")
    void unknownEscalates() {
        Decision d = policy.apply(failure("X", "", "", ""), Verdict.unknown("llm", "bad json"));

        assertThat(d.action()).isEqualTo(Action.ESCALATE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PPPPPPPP", "PPPPPPPF", "FFFFFFFF"})
    @DisplayName("'flaky' without unstable history is escalated, not re-run")
    void flakyNeedsEvidence(String history) {
        Decision d = policy.apply(failure("TimeoutException", "", "", history),
                verdict(Category.FLAKY_TEST, 0.9, Action.RERUN));

        assertThat(d.action()).isEqualTo(Action.ESCALATE);
    }

    @Test
    @DisplayName("flaky with unstable history may be re-run")
    void flakyWithEvidenceIsRerun() {
        Decision d = policy.apply(failure("TimeoutException", "", "", "PPFPPFPP"),
                verdict(Category.FLAKY_TEST, 0.9, Action.RERUN));

        assertThat(d.action()).isEqualTo(Action.RERUN);
        assertThat(d.overriddenByPolicy()).isFalse();
    }

    @Test
    @DisplayName("quarantine needs repeated instability, otherwise it becomes a re-run")
    void quarantineNeedsMoreFlips() {
        FailureCase twoFlips = failure("TimeoutException", "", "", "PPPPPFPP");
        FailureCase manyFlips = failure("TimeoutException", "", "", "PFPFPFPP");

        assertThat(policy.apply(twoFlips, verdict(Category.FLAKY_TEST, 0.9, Action.QUARANTINE)).action())
                .isEqualTo(Action.RERUN);
        assertThat(policy.apply(manyFlips, verdict(Category.FLAKY_TEST, 0.9, Action.QUARANTINE)).action())
                .isEqualTo(Action.QUARANTINE);
    }

    @Test
    @DisplayName("an action that does not fit the category falls back to the category default")
    void disallowedActionFallsBack() {
        Decision d = policy.apply(failure("ConnectException", "", "", ""),
                verdict(Category.ENVIRONMENT, 0.9, Action.UPDATE_LOCATOR));

        assertThat(d.action()).isEqualTo(Action.NOTIFY_INFRA);
        assertThat(d.overriddenByPolicy()).isTrue();
    }
}
