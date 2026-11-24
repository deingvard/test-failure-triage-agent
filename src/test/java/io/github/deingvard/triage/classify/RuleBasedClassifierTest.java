package io.github.deingvard.triage.classify;

import io.github.deingvard.triage.model.Category;
import io.github.deingvard.triage.model.FailureCase;
import io.github.deingvard.triage.model.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.github.deingvard.triage.TestCases.failure;
import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedClassifierTest {

    private final RuleBasedClassifier classifier = new RuleBasedClassifier();

    static Stream<Arguments> typicalFailures() {
        return Stream.of(
                Arguments.of(failure("java.net.ConnectException", "Connection refused: api:8443", "", "PPPP"),
                        Category.ENVIRONMENT),
                Arguments.of(failure("org.postgresql.util.PSQLException",
                        "duplicate key value violates unique constraint", "", "PPPP"), Category.TEST_DATA),
                Arguments.of(failure("org.openqa.selenium.NoSuchElementException",
                        "Unable to locate element #pay", "", "PPPPPPPP"), Category.BROKEN_LOCATOR),
                Arguments.of(failure("org.openqa.selenium.StaleElementReferenceException",
                        "stale element reference", "", "PFPPFPPF"), Category.FLAKY_TEST),
                Arguments.of(failure("java.lang.AssertionError", "Expected status code <200> but was <500>.",
                        "ERROR [svc] java.lang.NullPointerException", "PPPPPPPP"), Category.PRODUCT_BUG));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("typicalFailures")
    @DisplayName("classifies typical failures")
    void classifiesTypicalFailures(FailureCase failure, Category expected) {
        Verdict v = classifier.classify(failure);

        assertThat(v.category()).isEqualTo(expected);
        assertThat(v.reasons()).isNotEmpty();
        assertThat(v.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("an empty failure defaults to a low-confidence product bug instead of throwing")
    void emptyFailure() {
        Verdict v = classifier.classify(failure(null, null, null, null));

        assertThat(v.category()).isEqualTo(Category.PRODUCT_BUG);
        assertThat(v.confidence()).isLessThan(0.5);
    }
}
