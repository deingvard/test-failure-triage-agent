package io.github.deingvard.triage.eval;

import io.github.deingvard.triage.classify.Classifier;
import io.github.deingvard.triage.guard.ActionPolicy;
import io.github.deingvard.triage.model.Action;
import io.github.deingvard.triage.model.Category;
import io.github.deingvard.triage.model.Decision;
import io.github.deingvard.triage.model.FailureCase;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a classifier plus the safety policy over the labelled dataset and computes quality metrics.
 * Besides accuracy it tracks the metric that matters most for a QA team: how many real product bugs
 * would have been silenced (re-run or quarantined).
 */
public final class Evaluator {

    private final Classifier classifier;
    private final ActionPolicy policy;

    public Evaluator(Classifier classifier, ActionPolicy policy) {
        this.classifier = classifier;
        this.policy = policy;
    }

    public EvaluationResult evaluate(List<FailureCase> dataset) {
        Map<Category, Map<Category, Integer>> confusion = new EnumMap<>(Category.class);
        for (Category actual : Category.values()) {
            Map<Category, Integer> row = new EnumMap<>(Category.class);
            for (Category predicted : Category.values()) {
                row.put(predicted, 0);
            }
            confusion.put(actual, row);
        }

        List<Decision> decisions = new ArrayList<>();
        int correct = 0;
        int escalated = 0;
        int silencedBugs = 0;
        for (FailureCase c : dataset) {
            if (c.label() == null) {
                throw new IllegalArgumentException("Case " + c.id() + " has no label; cannot evaluate");
            }
            Decision d = policy.apply(c, classifier.classify(c));
            decisions.add(d);
            Category predicted = d.verdict().category();
            confusion.get(c.label()).merge(predicted, 1, Integer::sum);
            if (predicted == c.label()) {
                correct++;
            }
            if (d.action() == Action.ESCALATE) {
                escalated++;
            }
            if (c.label() == Category.PRODUCT_BUG && ActionPolicy.SILENCING_ACTIONS.contains(d.action())) {
                silencedBugs++;
            }
        }
        return new EvaluationResult(classifier.name(), dataset.size(), correct, escalated, silencedBugs,
                confusion, decisions);
    }
}
