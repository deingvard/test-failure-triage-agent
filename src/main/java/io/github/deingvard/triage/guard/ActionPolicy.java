package io.github.deingvard.triage.guard;

import io.github.deingvard.triage.model.Action;
import io.github.deingvard.triage.model.Category;
import io.github.deingvard.triage.model.Decision;
import io.github.deingvard.triage.model.FailureCase;
import io.github.deingvard.triage.model.Verdict;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic guardrails between the classifier and the human. The classifier (rules or LLM) only
 * proposes; this policy decides which proposals are acceptable. The main goal is to never let a real
 * product bug be silenced by "rerun until green" or quarantine.
 */
public final class ActionPolicy {

    /** Actions that make a red test go away without anyone fixing the product. */
    public static final Set<Action> SILENCING_ACTIONS = EnumSet.of(Action.RERUN, Action.QUARANTINE);

    private static final Map<Category, Set<Action>> ALLOWED = Map.of(
            Category.PRODUCT_BUG, EnumSet.of(Action.FILE_BUG, Action.ESCALATE),
            Category.FLAKY_TEST, EnumSet.of(Action.RERUN, Action.QUARANTINE, Action.ESCALATE),
            Category.ENVIRONMENT, EnumSet.of(Action.NOTIFY_INFRA, Action.RERUN, Action.ESCALATE),
            Category.TEST_DATA, EnumSet.of(Action.FIX_TEST_DATA, Action.ESCALATE),
            Category.BROKEN_LOCATOR, EnumSet.of(Action.UPDATE_LOCATOR, Action.ESCALATE),
            Category.UNKNOWN, EnumSet.of(Action.ESCALATE));

    /** Pass/fail switches in history required before a failure may be treated as flaky at all. */
    public static final int MIN_FLIPS_FOR_FLAKY = 2;
    /** A test must be repeatedly unstable before it may be quarantined. */
    public static final int MIN_FLIPS_FOR_QUARANTINE = 4;

    private final double minConfidence;

    public ActionPolicy(double minConfidence) {
        if (minConfidence < 0 || minConfidence > 1) {
            throw new IllegalArgumentException("minConfidence must be within [0, 1]");
        }
        this.minConfidence = minConfidence;
    }

    public static Action defaultActionFor(Category category) {
        return switch (category) {
            case PRODUCT_BUG -> Action.FILE_BUG;
            case FLAKY_TEST -> Action.RERUN;
            case ENVIRONMENT -> Action.NOTIFY_INFRA;
            case TEST_DATA -> Action.FIX_TEST_DATA;
            case BROKEN_LOCATOR -> Action.UPDATE_LOCATOR;
            case UNKNOWN -> Action.ESCALATE;
        };
    }

    public Decision apply(FailureCase failure, Verdict verdict) {
        List<String> notes = new ArrayList<>();
        Action action = verdict.suggestedAction();
        Category category = verdict.category();

        if (category == Category.UNKNOWN) {
            return new Decision(failure, verdict, Action.ESCALATE,
                    List.of("No valid verdict, handing over to a human."));
        }

        if (verdict.confidence() < minConfidence) {
            notes.add(String.format("Confidence %.2f is below the %.2f threshold.", verdict.confidence(), minConfidence));
            return new Decision(failure, verdict, Action.ESCALATE, notes);
        }

        if (!ALLOWED.get(category).contains(action)) {
            Action fallback = defaultActionFor(category);
            notes.add(action + " is not allowed for " + category + "; using " + fallback + ".");
            action = fallback;
        }

        if (category == Category.FLAKY_TEST && failure.historyFlips() < MIN_FLIPS_FOR_FLAKY) {
            notes.add("Classified as flaky, but run history shows " + failure.historyFlips()
                    + " pass/fail switch(es); at least " + MIN_FLIPS_FOR_FLAKY
                    + " are needed as evidence. Not re-running a possibly real failure.");
            return new Decision(failure, verdict, Action.ESCALATE, notes);
        }

        if (action == Action.QUARANTINE && failure.historyFlips() < MIN_FLIPS_FOR_QUARANTINE) {
            notes.add("Quarantine needs at least " + MIN_FLIPS_FOR_QUARANTINE
                    + " pass/fail flips in history; found " + failure.historyFlips() + ". Re-run instead.");
            action = Action.RERUN;
        }

        return new Decision(failure, verdict, action, notes);
    }
}
