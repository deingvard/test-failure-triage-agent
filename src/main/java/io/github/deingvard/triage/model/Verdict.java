package io.github.deingvard.triage.model;

import java.util.List;

/**
 * Raw output of a classifier, before the safety policy is applied.
 *
 * @param category        predicted root cause
 * @param confidence      0.0 – 1.0
 * @param suggestedAction what the classifier proposes
 * @param reasons         human-readable evidence for the verdict
 * @param classifier      name of the classifier that produced it
 */
public record Verdict(Category category, double confidence, Action suggestedAction,
                      List<String> reasons, String classifier) {

    public Verdict {
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        reasons = List.copyOf(reasons);
    }

    public static Verdict unknown(String classifier, String reason) {
        return new Verdict(Category.UNKNOWN, 0.0, Action.ESCALATE, List.of(reason), classifier);
    }
}
