package io.github.deingvard.triage.eval;

import io.github.deingvard.triage.classify.Classifier;
import io.github.deingvard.triage.classify.RuleBasedClassifier;
import io.github.deingvard.triage.guard.ActionPolicy;
import io.github.deingvard.triage.io.DatasetReader;
import io.github.deingvard.triage.model.Action;
import io.github.deingvard.triage.model.Category;
import io.github.deingvard.triage.model.FailureCase;
import io.github.deingvard.triage.model.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quality gates on the labelled dataset. These run in CI, so a change to the rules or the policy
 * that lowers accuracy or lets a product bug be silenced fails the build.
 */
class EvaluatorTest {

    private static final List<FailureCase> DATASET = DatasetReader.read(Path.of("data/dataset.jsonl"));
    private static final ActionPolicy POLICY = new ActionPolicy(0.6);

    @Test
    @DisplayName("dataset is labelled, has unique ids and is balanced across categories")
    void datasetIsWellFormed() {
        assertThat(DATASET).hasSizeGreaterThanOrEqualTo(50).allSatisfy(c -> assertThat(c.label()).isNotNull());
        assertThat(DATASET).extracting(FailureCase::id).doesNotHaveDuplicates();

        Map<Category, Long> perCategory = DATASET.stream()
                .collect(Collectors.groupingBy(FailureCase::label, Collectors.counting()));
        assertThat(perCategory.keySet()).containsExactlyInAnyOrder(Category.assignable());
        assertThat(perCategory.values()).allSatisfy(n -> assertThat(n).isGreaterThanOrEqualTo(10));
    }

    @Test
    @DisplayName("rule baseline: accuracy >= 85% and no product bug silenced")
    void ruleBaselineQualityGate() {
        EvaluationResult result = new Evaluator(new RuleBasedClassifier(), POLICY).evaluate(DATASET);

        assertThat(result.accuracy()).isGreaterThanOrEqualTo(0.85);
        assertThat(result.silencedBugs()).isZero();
        assertThat(result.recall(Category.PRODUCT_BUG)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("the policy stops even a classifier that tries to silence every failure")
    void policyProtectsAgainstAdversarialClassifier() {
        Classifier silenceEverything = new Classifier() {
            @Override
            public String name() {
                return "adversarial";
            }

            @Override
            public Verdict classify(FailureCase f) {
                return new Verdict(Category.FLAKY_TEST, 1.0, Action.QUARANTINE, List.of("trust me"), name());
            }
        };

        EvaluationResult result = new Evaluator(silenceEverything, POLICY).evaluate(DATASET);

        assertThat(result.silencedBugs()).isZero();
    }

    @Test
    @DisplayName("metrics are computed correctly on a tiny hand-checked example")
    void metricsArithmetic() {
        List<FailureCase> tiny = Arrays.stream(Category.assignable())
                .map(c -> new FailureCase(c.name(), "T#" + c, "api", "", "", "", "", "PPPP", c))
                .toList();
        Function<FailureCase, Category> alwaysBug = f -> Category.PRODUCT_BUG;
        Classifier classifier = new Classifier() {
            @Override
            public String name() {
                return "always-bug";
            }

            @Override
            public Verdict classify(FailureCase f) {
                return new Verdict(alwaysBug.apply(f), 0.9, Action.FILE_BUG, List.of("x"), name());
            }
        };

        EvaluationResult r = new Evaluator(classifier, POLICY).evaluate(tiny);

        assertThat(r.accuracy()).isEqualTo(0.2);
        assertThat(r.precision(Category.PRODUCT_BUG)).isEqualTo(0.2);
        assertThat(r.recall(Category.PRODUCT_BUG)).isEqualTo(1.0);
        assertThat(r.recall(Category.TEST_DATA)).isZero();
        assertThat(r.misclassified()).hasSize(4);
    }
}
