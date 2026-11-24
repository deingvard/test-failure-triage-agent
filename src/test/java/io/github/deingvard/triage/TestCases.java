package io.github.deingvard.triage;

import io.github.deingvard.triage.model.FailureCase;

/** Small builder for hand-written failure cases in unit tests. */
public final class TestCases {

    private TestCases() {
    }

    public static FailureCase failure(String errorType, String message, String log, String history) {
        return new FailureCase("t-1", "SomeTest#someMethod", "api", errorType, message, "", log, history, null);
    }
}
