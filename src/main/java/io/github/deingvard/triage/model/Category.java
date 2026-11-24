package io.github.deingvard.triage.model;

/**
 * Root-cause categories for a failed automated test.
 */
public enum Category {
    PRODUCT_BUG("The system under test behaves incorrectly: wrong business result, server-side exception, broken contract."),
    FLAKY_TEST("The test is unstable: timing, async waits, race conditions; the same code passes and fails on different runs."),
    ENVIRONMENT("Infrastructure problem: service down, network, DNS, TLS, browser grid, disk, database unavailable."),
    TEST_DATA("The test's own data or preconditions are wrong: duplicates, missing fixtures, locked or exhausted test accounts."),
    BROKEN_LOCATOR("A UI selector no longer matches after a front-end change; the element exists under another locator."),
    UNKNOWN("The classifier could not produce a valid verdict.");

    private final String definition;

    Category(String definition) {
        this.definition = definition;
    }

    public String definition() {
        return definition;
    }

    /** Categories a classifier is allowed to return (UNKNOWN is reserved for invalid output). */
    public static Category[] assignable() {
        return new Category[]{PRODUCT_BUG, FLAKY_TEST, ENVIRONMENT, TEST_DATA, BROKEN_LOCATOR};
    }
}
