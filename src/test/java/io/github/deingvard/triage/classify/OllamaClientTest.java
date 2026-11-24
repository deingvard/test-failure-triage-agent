package io.github.deingvard.triage.classify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the client against a tiny local HTTP stub that mimics the Ollama chat API. */
class OllamaClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private HttpServer server;

    private String startStub(int status, String responseBody, AtomicReference<String> captured) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("sends a deterministic JSON-mode chat request and returns the message content")
    void happyPath() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        String url = startStub(200, "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"category\\\":\\\"FLAKY_TEST\\\"}\"}}",
                request);

        String answer = new OllamaClient(url, "qwen2.5:7b").complete("system rules", "user failure");

        assertThat(answer).isEqualTo("{\"category\":\"FLAKY_TEST\"}");
        JsonNode sent = MAPPER.readTree(request.get());
        assertThat(sent.path("model").asText()).isEqualTo("qwen2.5:7b");
        assertThat(sent.path("stream").asBoolean()).isFalse();
        assertThat(sent.path("format").asText()).isEqualTo("json");
        assertThat(sent.path("options").path("temperature").asInt()).isZero();
        assertThat(sent.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(sent.path("messages").get(1).path("content").asText()).isEqualTo("user failure");
    }

    @Test
    @DisplayName("reports Ollama errors such as a model that was not pulled")
    void modelNotFound() throws Exception {
        String url = startStub(404, "{\"error\":\"model 'qwen2.5:7b' not found\"}", new AtomicReference<>());

        assertThatThrownBy(() -> new OllamaClient(url, null).complete("s", "u"))
                .isInstanceOf(LlmClient.LlmException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("an unreachable server becomes an LlmException with a hint, not a crash")
    void serverDown() {
        OllamaClient client = new OllamaClient("http://127.0.0.1:1", "m");

        assertThatThrownBy(() -> client.complete("s", "u"))
                .isInstanceOf(LlmClient.LlmException.class)
                .hasMessageContaining("ollama serve");
    }

    @Test
    void defaults() {
        assertThat(new OllamaClient(null, " ").model()).isEqualTo(OllamaClient.DEFAULT_MODEL);
    }
}
