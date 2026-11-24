package io.github.deingvard.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One failed test execution, normalised from a JUnit/Surefire report or from the labelled dataset.
 *
 * @param id          stable identifier of the case
 * @param testName    fully qualified test name, e.g. {@code PaymentsApiTest#refundIsIdempotent}
 * @param suite       suite type: api, ui, integration, e2e
 * @param errorType   exception class reported by the test runner
 * @param message     failure message
 * @param stackTrace  stack trace (may be truncated)
 * @param log         relevant log excerpt captured during the run
 * @param history     previous results of the same test, oldest first: P = passed, F = failed
 * @param label       ground-truth category; present only in the evaluation dataset
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FailureCase(
        @JsonProperty("id") String id,
        @JsonProperty("test_name") String testName,
        @JsonProperty("suite") String suite,
        @JsonProperty("error_type") String errorType,
        @JsonProperty("message") String message,
        @JsonProperty("stack_trace") String stackTrace,
        @JsonProperty("log") String log,
        @JsonProperty("history") String history,
        @JsonProperty("label") Category label) {

    public FailureCase {
        suite = nullToEmpty(suite);
        errorType = nullToEmpty(errorType);
        message = nullToEmpty(message);
        stackTrace = nullToEmpty(stackTrace);
        log = nullToEmpty(log);
        history = nullToEmpty(history).toUpperCase();
    }

    /** Number of pass/fail switches in the history, a simple instability signal. */
    public int historyFlips() {
        int flips = 0;
        for (int i = 1; i < history.length(); i++) {
            if (history.charAt(i) != history.charAt(i - 1)) {
                flips++;
            }
        }
        return flips;
    }

    /** True when the test passed on every recorded previous run. */
    public boolean previouslyStable() {
        return !history.isEmpty() && history.chars().allMatch(c -> c == 'P');
    }

    /** All free text of the case in one lower-case string, for pattern matching. */
    public String searchableText() {
        return String.join("\n", errorType, message, stackTrace, log).toLowerCase();
    }

    public FailureCase withHistory(String newHistory) {
        return new FailureCase(id, testName, suite, errorType, message, stackTrace, log, newHistory, label);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
