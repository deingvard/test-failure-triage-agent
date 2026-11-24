package io.github.deingvard.triage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.deingvard.triage.classify.AnthropicClient;
import io.github.deingvard.triage.classify.Classifier;
import io.github.deingvard.triage.classify.LlmClassifier;
import io.github.deingvard.triage.classify.OllamaClient;
import io.github.deingvard.triage.classify.RuleBasedClassifier;
import io.github.deingvard.triage.eval.EvaluationResult;
import io.github.deingvard.triage.eval.Evaluator;
import io.github.deingvard.triage.guard.ActionPolicy;
import io.github.deingvard.triage.guard.Redactor;
import io.github.deingvard.triage.io.DatasetReader;
import io.github.deingvard.triage.io.JUnitXmlReader;
import io.github.deingvard.triage.model.Decision;
import io.github.deingvard.triage.model.FailureCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point.
 * <pre>
 *   triage evaluate data/dataset.jsonl [--classifier rules|llm|ollama] [--out results] [--min-confidence 0.6]
 *   triage report   data/sample-report [--history data/sample-report/history.json]
 *                   [--classifier rules|llm|ollama] [--interactive] [--out results] [--min-confidence 0.6]
 * </pre>
 */
public final class TriageCli {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TriageCli() {
    }

    public static void main(String[] args) throws IOException {
        try {
            run(args);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void run(String[] args) throws IOException {
        if (args.length < 2) {
            usage();
            System.exit(2);
        }
        Map<String, String> opts = options(args);
        Classifier classifier = classifier(opts.getOrDefault("classifier", "rules"));
        ActionPolicy policy = new ActionPolicy(Double.parseDouble(opts.getOrDefault("min-confidence", "0.6")));
        Path out = Path.of(opts.getOrDefault("out", "results"));
        Files.createDirectories(out);

        switch (args[0]) {
            case "evaluate" -> evaluate(Path.of(args[1]), classifier, policy, out);
            case "report" -> report(Path.of(args[1]), opts.get("history"), classifier, policy, out,
                    opts.containsKey("interactive"));
            default -> {
                usage();
                System.exit(2);
            }
        }
    }

    private static void evaluate(Path dataset, Classifier classifier, ActionPolicy policy, Path out) throws IOException {
        List<FailureCase> cases = DatasetReader.read(dataset);
        EvaluationResult result = new Evaluator(classifier, policy).evaluate(cases);
        Path file = out.resolve("evaluation-" + classifier.name() + ".md");
        Files.writeString(file, result.toMarkdown(), StandardCharsets.UTF_8);
        System.out.println(result.summary());
        System.out.println("Report written to " + file);
        if (result.silencedBugs() > 0) {
            System.err.println("FAIL: " + result.silencedBugs() + " product bug(s) would have been silenced.");
            System.exit(1);
        }
    }

    private static void report(Path reports, String history, Classifier classifier, ActionPolicy policy,
                               Path out, boolean interactive) throws IOException {
        List<FailureCase> failures = JUnitXmlReader.read(reports, history == null ? null : Path.of(history));
        if (failures.isEmpty()) {
            System.out.println("No failed tests found in " + reports);
            return;
        }
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        List<String> audit = new ArrayList<>();
        StringBuilder md = new StringBuilder("# Triage of ").append(reports).append("\n\n")
                .append("| Test | Category | Confidence | Proposed action | Status |\n|---|---|---|---|---|\n");

        for (FailureCase f : failures) {
            Decision d = policy.apply(f, classifier.classify(f));
            print(d);
            String status = "PENDING_APPROVAL";
            if (interactive) {
                System.out.print("Approve " + d.action() + " for " + f.testName() + "? [y/N] ");
                String answer = stdin.readLine();
                status = answer != null && answer.strip().equalsIgnoreCase("y") ? "APPROVED" : "REJECTED";
                System.out.println(status);
            }
            audit.add(auditLine(d, status));
            md.append(String.format("| %s | %s | %.2f | %s | %s |%n", f.testName(), d.verdict().category(),
                    d.verdict().confidence(), d.action(), status));
        }

        Path auditFile = out.resolve("audit.jsonl");
        Files.write(auditFile, audit, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        Path mdFile = out.resolve("triage-report.md");
        Files.writeString(mdFile, md.toString(), StandardCharsets.UTF_8);
        System.out.println("\n" + failures.size() + " failure(s) triaged. Report: " + mdFile + ", audit log: " + auditFile);
        if (!interactive) {
            System.out.println("Nothing was executed. Re-run with --interactive to approve or reject each proposal.");
        }
    }

    private static void print(Decision d) {
        System.out.println();
        System.out.println("> " + d.failure().testName());
        System.out.printf("  category:   %s (confidence %.2f, by %s)%n", d.verdict().category(),
                d.verdict().confidence(), d.verdict().classifier());
        for (String r : d.verdict().reasons()) {
            System.out.println("  evidence:   " + Redactor.redact(r).text());
        }
        System.out.println("  proposed:   " + d.action() + " - " + d.action().description());
        for (String n : d.policyNotes()) {
            System.out.println("  policy:     " + n);
        }
    }

    private static String auditLine(Decision d, String status) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("timestamp", Instant.now().toString());
        n.put("test", d.failure().testName());
        n.put("classifier", d.verdict().classifier());
        n.put("category", d.verdict().category().name());
        n.put("confidence", d.verdict().confidence());
        n.put("suggested_action", d.verdict().suggestedAction().name());
        n.put("final_action", d.action().name());
        n.put("status", status);
        d.verdict().reasons().forEach(r -> n.withArray("evidence").add(Redactor.redact(r).text()));
        d.policyNotes().forEach(p -> n.withArray("policy_notes").add(p));
        return n.toString();
    }

    private static Classifier classifier(String name) {
        return switch (name) {
            case "rules" -> new RuleBasedClassifier();
            case "llm" -> new LlmClassifier(AnthropicClient.fromEnvironment(), "llm");
            case "ollama" -> new LlmClassifier(OllamaClient.fromEnvironment(), "ollama");
            default -> throw new IllegalArgumentException("Unknown classifier: " + name + " (use rules, llm or ollama)");
        };
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 2; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + args[i]);
            }
            String key = args[i].substring(2);
            if (key.equals("interactive")) {
                opts.put(key, "true");
            } else if (i + 1 < args.length) {
                opts.put(key, args[++i]);
            } else {
                throw new IllegalArgumentException("Missing value for --" + key);
            }
        }
        return opts;
    }

    private static void usage() {
        System.err.println("""
                Usage:
                  triage evaluate <dataset.jsonl> [--classifier rules|llm|ollama] [--out results] [--min-confidence 0.6]
                  triage report <junit-report-dir|file> [--history history.json] [--classifier rules|llm|ollama]
                                [--interactive] [--out results] [--min-confidence 0.6]
                """);
    }
}
