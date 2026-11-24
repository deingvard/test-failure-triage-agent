package io.github.deingvard.triage.classify;

import io.github.deingvard.triage.model.FailureCase;
import io.github.deingvard.triage.model.Verdict;

/**
 * Maps a failed test to a root-cause verdict. Implementations must never throw for bad input;
 * when they cannot decide they return {@link Verdict#unknown(String, String)}.
 */
public interface Classifier {

    String name();

    Verdict classify(FailureCase failure);
}
