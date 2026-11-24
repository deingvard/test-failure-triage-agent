package io.github.deingvard.triage.model;

/**
 * Next steps the agent may propose. Nothing is executed automatically: every action is a proposal
 * that a human approves or rejects.
 */
public enum Action {
    FILE_BUG("Open a defect for the development team with the failure evidence attached."),
    RERUN("Re-run the test once to confirm instability."),
    QUARANTINE("Move the test to the quarantine suite and open a ticket to stabilise it."),
    NOTIFY_INFRA("Notify the infrastructure / DevOps owner; do not touch the test."),
    FIX_TEST_DATA("Repair or regenerate the test data / preconditions."),
    UPDATE_LOCATOR("Update the UI selector in the page object."),
    ESCALATE("Hand over to a human for manual analysis.");

    private final String description;

    Action(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
