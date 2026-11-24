package io.github.deingvard.triage.model;

import java.util.List;

/**
 * Final proposal after the safety policy: what the agent recommends and why it may differ from the
 * classifier's own suggestion. Every decision still needs human approval.
 *
 * @param failure     the failure being triaged
 * @param verdict     the classifier's raw verdict
 * @param action      the action that survived the policy
 * @param policyNotes explanations for every change the policy made
 */
public record Decision(FailureCase failure, Verdict verdict, Action action, List<String> policyNotes) {

    public Decision {
        policyNotes = List.copyOf(policyNotes);
    }

    public boolean overriddenByPolicy() {
        return action != verdict.suggestedAction();
    }
}
