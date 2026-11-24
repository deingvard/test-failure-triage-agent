package io.github.deingvard.triage.classify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls a local open-weights model through the Ollama chat API. Free, offline and private: the
 * failure text never leaves the machine. Configuration comes from environment variables:
 * <ul>
 *     <li>{@code OLLAMA_URL} – optional, defaults to {@value #DEFAULT_URL}</li>
 *     <li>{@code OLLAMA_MODEL} – optional, defaults to {@value #DEFAULT_MODEL}</li>
 * </ul>
 */
public final class OllamaClient implements LlmClient {

    public static final String DEFAULT_URL = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "qwen2.5:7b";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final URI endpoint;
    private final String model;

    public OllamaClient(String baseUrl, String model) {
        String base = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_URL : baseUrl.replaceAll("/+$", "");
        this.endpoint = URI.create(base + "/api/chat");
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public static OllamaClient fromEnvironment() {
        return new OllamaClient(System.getenv("OLLAMA_URL"), System.getenv("OLLAMA_MODEL"));
    }

    public String model() {
        return model;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws LlmException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.put("format", "json");
        body.putObject("options").put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMinutes(5)) // CPU-only inference can be slow
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = MAPPER.readTree(response.body());
            if (response.statusCode() != 200) {
                throw new LlmException("Ollama returned HTTP " + response.statusCode() + ": "
                        + json.path("error").asText(response.body()));
            }
            return json.path("message").path("content").asText("");
        } catch (IOException e) {
            throw new LlmException("Ollama call failed (is `ollama serve` running at " + endpoint + "?): "
                    + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while calling Ollama", e);
        }
    }
}
