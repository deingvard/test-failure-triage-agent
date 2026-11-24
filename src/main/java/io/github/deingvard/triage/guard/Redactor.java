package io.github.deingvard.triage.guard;

import io.github.deingvard.triage.model.FailureCase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks secrets and personal data in failure text before it leaves the machine (LLM call, audit log).
 * Runs locally and is regex-based; it is a safety net, not a guarantee, and errs on the side of masking.
 */
public final class Redactor {

    private record Rule(String name, Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("jwt", Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"), "[JWT]"),
            new Rule("bearer", Pattern.compile("(?i)\\bbearer\\s+(?!\\[JWT])[A-Za-z0-9._~+/=-]{8,}"), "Bearer [TOKEN]"),
            new Rule("api_key", Pattern.compile("\\b(?:sk|pk|rk)-[A-Za-z0-9_-]{16,}"), "[API_KEY]"),
            new Rule("secret_param", Pattern.compile(
                    "(?i)\\b(password|passwd|pwd|secret|api[_-]?key|access[_-]?token|client[_-]?secret)(\\s*[=:]\\s*)[^\\s,;\"'}]+"),
                    "$1$2[SECRET]"),
            new Rule("email", Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[EMAIL]"),
            new Rule("iban", Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){3,7}(?: ?[A-Z0-9]{1,3})?\\b"), "[IBAN]"),
            new Rule("ipv4", Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b"), "[IP]")
    );

    /** Digits with optional single spaces or dashes, 13–19 digits long. Checked with Luhn before masking. */
    private static final Pattern CARD_CANDIDATE = Pattern.compile("\\b\\d(?:[ -]?\\d){12,18}\\b");

    private Redactor() {
    }

    /** Result of redacting one text: the masked text and how many items of each kind were masked. */
    public record Result(String text, Map<String, Integer> counts) {
        public int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public static Result redact(String text) {
        if (text == null || text.isEmpty()) {
            return new Result(text == null ? "" : text, Map.of());
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        String out = maskCards(text, counts);
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(out);
            int n = 0;
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                n++;
                m.appendReplacement(sb, rule.replacement());
            }
            m.appendTail(sb);
            if (n > 0) {
                counts.merge(rule.name(), n, Integer::sum);
                out = sb.toString();
            }
        }
        return new Result(out, Map.copyOf(counts));
    }

    /** Returns a copy of the case with every free-text field redacted. */
    public static FailureCase redact(FailureCase c) {
        return new FailureCase(c.id(), c.testName(), c.suite(), c.errorType(),
                redact(c.message()).text(), redact(c.stackTrace()).text(), redact(c.log()).text(),
                c.history(), c.label());
    }

    private static String maskCards(String text, Map<String, Integer> counts) {
        Matcher m = CARD_CANDIDATE.matcher(text);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        while (m.find()) {
            String digits = m.group().replaceAll("[ -]", "");
            if (luhnValid(digits)) {
                n++;
                m.appendReplacement(sb, "[CARD]");
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        if (n > 0) {
            counts.merge("card", n, Integer::sum);
        }
        return sb.toString();
    }

    static boolean luhnValid(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
