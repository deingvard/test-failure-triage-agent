package io.github.deingvard.triage.classify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls the Anthropic Messages API with the JDK HTTP client (no SDK dependency).
 * Configuration comes from environment variables:
 * <ul>
 *     <li>{@code ANTHROPIC_API_KEY} – required</li>
 *     <li>{@code TRIAGE_MODEL} – optional model id, defaults to {@value #DEFAULT_MODEL}</li>
 * </ul>
 */
public final class AnthropicClient implements LlmClient {

    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    private static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final String apiKey;
    private final String model;

    public AnthropicClient(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ANTHROPIC_API_KEY is not set");
        }
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public static AnthropicClient fromEnvironment() {
        return new AnthropicClient(System.getenv("ANTHROPIC_API_KEY"), System.getenv("TRIAGE_MODEL"));
    }

    public String model() {
        return model;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws LlmException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 500);
        body.put("temperature", 0);
        body.put("system", systemPrompt);
        ObjectNode message = body.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", userPrompt);

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("Anthropic API returned HTTP " + response.statusCode() + ": "
                        + abbreviate(response.body(), 300));
            }
            JsonNode content = MAPPER.readTree(response.body()).path("content");
            StringBuilder text = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            return text.toString();
        } catch (IOException e) {
            throw new LlmException("Anthropic API call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while calling Anthropic API", e);
        }
    }

    private static String abbreviate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
