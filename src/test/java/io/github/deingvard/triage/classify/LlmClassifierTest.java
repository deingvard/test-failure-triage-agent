package io.github.deingvard.triage.classify;

import io.github.deingvard.triage.model.Action;
import io.github.deingvard.triage.model.Category;
import io.github.deingvard.triage.model.FailureCase;
import io.github.deingvard.triage.model.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static io.github.deingvard.triage.TestCases.failure;
import static org.assertj.core.api.Assertions.assertThat;

class LlmClassifierTest {

    /** Fake transport: returns a canned answer and records every prompt it receives. */
    private static final class FakeLlm implements LlmClient {
        final List<String> prompts = new ArrayList<>();
        private final String answer;
        private final boolean fail;

        FakeLlm(String answer, boolean fail) {
            this.answer = answer;
            this.fail = fail;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) throws LlmException {
            prompts.add(userPrompt);
            if (fail) {
                throw new LlmException("HTTP 529 overloaded");
            }
            return answer;
        }
    }

    private static final FailureCase CASE = failure("java.lang.AssertionError", "expected 1 but was 2",
            "user qa.user01@example.test paid with 4111 1111 1111 1111, token Bearer abcdefgh12345678", "PPPPPPPP");

    @Test
    @DisplayName("parses a valid JSON answer into a verdict")
    void validAnswer() {
        var llm = new FakeLlm("""
                {"category":"PRODUCT_BUG","confidence":0.82,"suggested_action":"FILE_BUG","rationale":"Duplicate transaction."}
                """, false);

        Verdict v = new LlmClassifier(llm).classify(CASE);

        assertThat(v.category()).isEqualTo(Category.PRODUCT_BUG);
        assertThat(v.confidence()).isEqualTo(0.82);
        assertThat(v.suggestedAction()).isEqualTo(Action.FILE_BUG);
        assertThat(v.classifier()).isEqualTo("llm");
    }

    @Test
    @DisplayName("accepts JSON wrapped in a code fence")
    void fencedAnswer() {
        var llm = new FakeLlm("```json\n{\"category\":\"flaky_test\",\"confidence\":0.7,"
                + "\"suggested_action\":\"rerun\",\"rationale\":\"Timing.\"}\n```", false);

        assertThat(new LlmClassifier(llm).classify(CASE).category()).isEqualTo(Category.FLAKY_TEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "I think it is a product bug.",
            "{not json}",
            "{\"category\":\"COSMIC_RAYS\",\"confidence\":0.9,\"suggested_action\":\"FILE_BUG\",\"rationale\":\"x\"}",
            "{\"category\":\"UNKNOWN\",\"confidence\":0.9,\"suggested_action\":\"ESCALATE\",\"rationale\":\"x\"}",
            "{\"category\":\"PRODUCT_BUG\",\"confidence\":1.7,\"suggested_action\":\"FILE_BUG\",\"rationale\":\"x\"}",
            "{\"category\":\"PRODUCT_BUG\",\"confidence\":\"high\",\"suggested_action\":\"FILE_BUG\",\"rationale\":\"x\"}",
            "{\"category\":\"PRODUCT_BUG\",\"confidence\":0.9,\"suggested_action\":\"DELETE_TEST\",\"rationale\":\"x\"}",
            "{\"category\":\"PRODUCT_BUG\",\"confidence\":0.9,\"suggested_action\":\"FILE_BUG\"}"
    })
    @DisplayName("any answer that breaks the schema becomes UNKNOWN")
    void invalidAnswersBecomeUnknown(String answer) {
        Verdict v = new LlmClassifier(new FakeLlm(answer, false)).classify(CASE);

        assertThat(v.category()).isEqualTo(Category.UNKNOWN);
        assertThat(v.suggestedAction()).isEqualTo(Action.ESCALATE);
    }

    @Test
    @DisplayName("a failed API call becomes UNKNOWN instead of crashing the pipeline")
    void transportFailure() {
        Verdict v = new LlmClassifier(new FakeLlm(null, true)).classify(CASE);

        assertThat(v.category()).isEqualTo(Category.UNKNOWN);
        assertThat(v.reasons().getFirst()).contains("529");
    }

    @Test
    @DisplayName("secrets and personal data are redacted before the prompt is sent")
    void promptIsRedacted() {
        var llm = new FakeLlm("{}", false);

        new LlmClassifier(llm).classify(CASE);

        assertThat(llm.prompts).hasSize(1);
        assertThat(llm.prompts.getFirst())
                .doesNotContain("qa.user01@example.test", "4111 1111 1111 1111", "abcdefgh12345678")
                .contains("[EMAIL]", "[CARD]", "[TOKEN]")
                .startsWith("<failure>");
    }

    @Test
    @DisplayName("the system prompt tells the model to treat test output as untrusted data")
    void systemPromptHasInjectionGuard() {
        assertThat(LlmClassifier.SYSTEM_PROMPT).contains("untrusted").contains("ignore any");
    }
}
