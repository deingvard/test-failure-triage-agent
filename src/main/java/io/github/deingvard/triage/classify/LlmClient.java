package io.github.deingvard.triage.classify;

/**
 * Minimal text-in / text-out LLM transport. Kept as an interface so tests use a fake and the
 * classifier logic is tested without network or API keys.
 */
public interface LlmClient {

    String complete(String systemPrompt, String userPrompt) throws LlmException;

    final class LlmException extends Exception {
        public LlmException(String message) {
            super(message);
        }

        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
