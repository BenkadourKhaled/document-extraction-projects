package dz.DocuScan.document.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Calls the local Ollama {@code /api/generate} endpoint with a non-streaming
 * JSON response.
 *
 * <p>Only the JDK HTTP client is used, so no vendor SDK or secret is required.
 * The request is constrained to a JSON answer and a deterministic temperature.
 * No prompt, response body or extracted value is logged in this class.</p>
 */
@Component
public class HttpOllamaClient implements OllamaClient {

    private final OllamaProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public HttpOllamaClient(OllamaProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(10, properties.timeoutSeconds())))
                .build();
    }

    @Override
    public String generate(String prompt) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", properties.model());
        payload.put("prompt", prompt == null ? "" : prompt);
        payload.put("stream", false);
        payload.put("format", "json");
        payload.put("keep_alive", properties.keepAlive());
        ObjectNode options = payload.putObject("options");
        options.put("temperature", 0);
        options.put("num_predict", properties.numPredict());
        options.put("num_ctx", properties.numContext());

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/api/generate"))
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
        } catch (RuntimeException | IOException ex) {
            throw new OllamaException("Ollama request could not be built.", ex);
        }

        HttpResponse<String> response;
        try {
            response = http.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OllamaException("Ollama request was interrupted.", ex);
        } catch (IOException ex) {
            throw new OllamaException("Ollama request failed.", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OllamaException("Ollama returned HTTP " + response.statusCode() + ".");
        }

        try {
            JsonNode root = mapper.readTree(response.body());
            if (root == null || !root.isObject()) {
                throw new OllamaException("Ollama returned an invalid response.");
            }
            if (root.has("error")) {
                throw new OllamaException("Ollama returned an error response.");
            }
            JsonNode generated = root.get("response");
            if (generated == null || !generated.isTextual() || generated.asText().isBlank()) {
                throw new OllamaException("Ollama returned an empty response.");
            }
            return generated.asText();
        } catch (IOException ex) {
            throw new OllamaException("Ollama returned an invalid response.", ex);
        }
    }
}
