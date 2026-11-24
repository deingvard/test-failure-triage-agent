package io.github.deingvard.triage.eval;

import io.github.deingvard.triage.model.Category;
import io.github.deingvard.triage.model.Decision;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Metrics of one evaluation run.
 *
 * @param classifier   classifier name
 * @param total        number of cases
 * @param correct      cases whose predicted category equals the label
 * @param escalated    cases the policy handed to a human
 * @param silencedBugs real product bugs that ended with RERUN or QUARANTINE; must be 0
 * @param confusion    actual category → predicted category → count
 * @param decisions    every decision, in dataset order
 */
public record EvaluationResult(String classifier, int total, int correct, int escalated, int silencedBugs,
                               Map<Category, Map<Category, Integer>> confusion, List<Decision> decisions) {

    public double accuracy() {
        return total == 0 ? 0 : (double) correct / total;
    }

    public double precision(Category c) {
        int predicted = confusion.values().stream().mapToInt(row -> row.get(c)).sum();
        return predicted == 0 ? 0 : (double) confusion.get(c).get(c) / predicted;
    }

    public double recall(Category c) {
        int actual = confusion.get(c).values().stream().mapToInt(Integer::intValue).sum();
        return actual == 0 ? 0 : (double) confusion.get(c).get(c) / actual;
    }

    public double f1(Category c) {
        double p = precision(c);
        double r = recall(c);
        return p + r == 0 ? 0 : 2 * p * r / (p + r);
    }

    public double macroF1() {
        return Arrays.stream(Category.assignable()).mapToDouble(this::f1).average().orElse(0);
    }

    public List<Decision> misclassified() {
        return decisions.stream().filter(d -> d.verdict().category() != d.failure().label()).toList();
    }

    public String summary() {
        return String.format("classifier=%s cases=%d accuracy=%.1f%% macroF1=%.3f escalated=%d silencedProductBugs=%d",
                classifier, total, accuracy() * 100, macroF1(), escalated, silencedBugs);
    }

    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("# Evaluation report: `").append(classifier).append("` classifier\n\n");
        md.append("| Metric | Value |\n|---|---|\n");
        md.append(String.format("| Cases | %d |%n", total));
        md.append(String.format("| Accuracy | %.1f%% (%d/%d) |%n", accuracy() * 100, correct, total));
        md.append(String.format("| Macro F1 | %.3f |%n", macroF1()));
        md.append(String.format("| Escalated to a human | %d |%n", escalated));
        md.append(String.format("| Product bugs silenced (RERUN/QUARANTINE) | %d |%n%n", silencedBugs));

        md.append("## Per category\n\n| Category | Precision | Recall | F1 |\n|---|---|---|---|\n");
        for (Category c : Category.assignable()) {
            md.append(String.format("| %s | %.2f | %.2f | %.2f |%n", c, precision(c), recall(c), f1(c)));
        }

        List<Category> cols = Arrays.asList(Category.values());
        md.append("\n## Confusion matrix (rows = actual, columns = predicted)\n\n| |");
        md.append(cols.stream().map(Category::name).collect(Collectors.joining(" | ", " ", " |\n")));
        md.append("|---|").append("---|".repeat(cols.size())).append('\n');
        for (Category actual : Category.assignable()) {
            md.append("| ").append(actual).append(" |");
            for (Category predicted : cols) {
                md.append(' ').append(confusion.get(actual).get(predicted)).append(" |");
            }
            md.append('\n');
        }

        List<Decision> wrong = misclassified();
        md.append("\n## Misclassified cases (").append(wrong.size()).append(")\n\n");
        if (!wrong.isEmpty()) {
            md.append("| Case | Actual | Predicted | Confidence | Final action |\n|---|---|---|---|---|\n");
            for (Decision d : wrong) {
                md.append(String.format("| %s | %s | %s | %.2f | %s |%n", d.failure().id(), d.failure().label(),
                        d.verdict().category(), d.verdict().confidence(), d.action()));
            }
        }
        return md.toString();
    }
}
