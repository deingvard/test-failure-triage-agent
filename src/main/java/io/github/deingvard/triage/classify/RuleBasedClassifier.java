package io.github.deingvard.triage.classify;

import io.github.deingvard.triage.guard.ActionPolicy;
import io.github.deingvard.triage.model.Category;
import io.github.deingvard.triage.model.FailureCase;
import io.github.deingvard.triage.model.Verdict;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Transparent keyword-and-history baseline. Every signal adds a weight to one category and leaves a
 * human-readable reason. It needs no network and no API key, so it is also the CI default and the
 * baseline the LLM classifier is compared against.
 */
public final class RuleBasedClassifier implements Classifier {

    private record Signal(Category category, int weight, String where, String needle, String reason) {
    }

    private static final String TYPE = "type";
    private static final String TEXT = "text";

    private static final List<Signal> SIGNALS = List.of(
            // environment
            new Signal(Category.ENVIRONMENT, 3, TYPE, "connectexception", "connection to a service failed"),
            new Signal(Category.ENVIRONMENT, 3, TYPE, "unknownhostexception", "DNS lookup failed"),
            new Signal(Category.ENVIRONMENT, 3, TYPE, "sslhandshakeexception", "TLS handshake failed"),
            new Signal(Category.ENVIRONMENT, 3, TYPE, "sessionnotcreatedexception", "browser session could not start"),
            new Signal(Category.ENVIRONMENT, 2, TEXT, "connection refused", "connection refused"),
            new Signal(Category.ENVIRONMENT, 2, TEXT, "no space left on device", "disk is full"),
            new Signal(Category.ENVIRONMENT, 2, TEXT, "database system is in recovery", "database unavailable"),
            new Signal(Category.ENVIRONMENT, 2, TEXT, "chrome not reachable", "browser process died"),
            new Signal(Category.ENVIRONMENT, 2, TEXT, "upstream connect error", "gateway could not reach upstream"),
            new Signal(Category.ENVIRONMENT, 2, TEXT, "no available nodes", "browser grid has no capacity"),
            new Signal(Category.ENVIRONMENT, 2, TEXT, "no free chrome nodes", "browser grid has no capacity"),
            new Signal(Category.ENVIRONMENT, 2, TEXT, "bad gateway", "proxy returned Bad Gateway"),
            new Signal(Category.ENVIRONMENT, 1, TEXT, "but was <503>", "HTTP 503 from the service"),
            new Signal(Category.ENVIRONMENT, 1, TEXT, "but was: <502>", "HTTP 502 from the service"),

            // test data
            new Signal(Category.TEST_DATA, 2, TEXT, "already exists", "entity already exists"),
            new Signal(Category.TEST_DATA, 2, TEXT, "duplicate key", "unique constraint violated by test data"),
            new Signal(Category.TEST_DATA, 2, TEXT, "insufficient_funds", "test account has no funds"),
            new Signal(Category.TEST_DATA, 2, TEXT, "account locked", "test user is locked"),
            new Signal(Category.TEST_DATA, 2, TEXT, "precondition failed", "test precondition not met"),
            new Signal(Category.TEST_DATA, 2, TEXT, "card_expired", "test card expired"),
            new Signal(Category.TEST_DATA, 2, TEXT, "fixtures/", "fixture file involved"),
            new Signal(Category.TEST_DATA, 2, TEXT, "test data", "test data mentioned"),
            new Signal(Category.TEST_DATA, 2, TEXT, "test-data", "test data mentioned"),
            new Signal(Category.TEST_DATA, 1, TEXT, "test account", "test account mentioned"),
            new Signal(Category.TEST_DATA, 1, TEXT, "but was <409>", "HTTP 409 Conflict"),
            new Signal(Category.TEST_DATA, 1, TEXT, "but was <423>", "HTTP 423 Locked"),

            // broken locator
            new Signal(Category.BROKEN_LOCATOR, 3, TYPE, "nosuchelementexception", "element not found by locator"),
            new Signal(Category.BROKEN_LOCATOR, 3, TYPE, "elementnotfound", "element not found by locator"),
            new Signal(Category.BROKEN_LOCATOR, 2, TEXT, "waiting for locator", "Playwright locator never matched"),
            new Signal(Category.BROKEN_LOCATOR, 2, TEXT, "resolved to 0 elements", "locator matches nothing"),
            new Signal(Category.BROKEN_LOCATOR, 1, TEXT, "renamed", "something was renamed in the UI"),
            new Signal(Category.BROKEN_LOCATOR, 1, TEXT, "replaced with", "UI component replaced"),

            // flaky
            new Signal(Category.FLAKY_TEST, 3, TYPE, "staleelementreferenceexception", "DOM re-rendered under the test"),
            new Signal(Category.FLAKY_TEST, 3, TYPE, "elementclickinterceptedexception", "overlay intercepted the click"),
            new Signal(Category.FLAKY_TEST, 2, TYPE, "conditiontimeoutexception", "async condition timed out"),
            new Signal(Category.FLAKY_TEST, 1, TYPE, "timeoutexception", "wait timed out"),
            new Signal(Category.FLAKY_TEST, 1, TEXT, "read timed out", "client read timeout"),
            new Signal(Category.FLAKY_TEST, 1, TEXT, "consumer lag", "message consumer is behind"),
            new Signal(Category.FLAKY_TEST, 1, TEXT, "still processing", "async processing not finished"),

            // product bug
            new Signal(Category.PRODUCT_BUG, 1, TYPE, "assertion", "assertion on the system's behaviour failed"),
            new Signal(Category.PRODUCT_BUG, 1, TYPE, "elementshouldnot", "UI shows an unexpected state"),
            new Signal(Category.PRODUCT_BUG, 2, TEXT, "schema validation failed", "response breaks the API contract"),
            new Signal(Category.PRODUCT_BUG, 2, TEXT, "but was <500>", "HTTP 500 from the service"),
            new Signal(Category.PRODUCT_BUG, 2, TEXT, "nullpointerexception", "server-side NullPointerException"),
            new Signal(Category.PRODUCT_BUG, 2, TEXT, "error [", "a service logged an ERROR during the test"),
            new Signal(Category.PRODUCT_BUG, 2, TEXT, "deadlock detected", "server-side deadlock"),
            new Signal(Category.PRODUCT_BUG, 2, TEXT, "outofmemoryerror", "server ran out of memory")
    );

    @Override
    public String name() {
        return "rules";
    }

    @Override
    public Verdict classify(FailureCase failure) {
        Map<Category, Integer> scores = new EnumMap<>(Category.class);
        Map<Category, List<String>> reasons = new EnumMap<>(Category.class);
        for (Category c : Category.assignable()) {
            scores.put(c, 0);
            reasons.put(c, new ArrayList<>());
        }

        String type = failure.errorType().toLowerCase();
        String text = failure.searchableText();
        for (Signal s : SIGNALS) {
            String haystack = TYPE.equals(s.where()) ? type : text;
            if (haystack.contains(s.needle())) {
                scores.merge(s.category(), s.weight(), Integer::sum);
                reasons.get(s.category()).add(s.reason());
            }
        }

        int flips = failure.historyFlips();
        if (flips >= 2) {
            scores.merge(Category.FLAKY_TEST, 3, Integer::sum);
            reasons.get(Category.FLAKY_TEST).add("history flips between pass and fail " + flips + " times");
        } else if (failure.previouslyStable()) {
            scores.merge(Category.PRODUCT_BUG, 1, Integer::sum);
            reasons.get(Category.PRODUCT_BUG).add("test was stable before, a new failure points to a change");
        }

        // PRODUCT_BUG comes first in the enum, so it wins ties: the safest default.
        Category best = Category.PRODUCT_BUG;
        int total = 0;
        for (Category c : Category.assignable()) {
            total += scores.get(c);
            if (scores.get(c) > scores.get(best)) {
                best = c;
            }
        }
        if (scores.get(best) == 0) {
            return new Verdict(Category.PRODUCT_BUG, 0.2, ActionPolicy.defaultActionFor(Category.PRODUCT_BUG),
                    List.of("no known signal matched; defaulting to product bug, which never hides a failure"), name());
        }
        double confidence = (double) scores.get(best) / (total + 1);
        return new Verdict(best, confidence, ActionPolicy.defaultActionFor(best), reasons.get(best), name());
    }
}
