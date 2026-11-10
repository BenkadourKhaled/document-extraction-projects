package dz.DocuScan.document.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpOllamaClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsNonStreamingJsonRequestAndReturnsModelResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<String> received = new AtomicReference<>();
        String generated = """
                {"reference_number":"2026-001","document_date":null}
                """;
        String ollamaBody = mapper.writeValueAsString(
                mapper.createObjectNode()
                        .put("response", generated)
                        .put("done", true));

        start(exchange -> {
            received.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, ollamaBody);
        });

        HttpOllamaClient client = new HttpOllamaClient(properties(port()), mapper);
        String response = client.generate("prompt");

        assertThat(response).contains("2026-001");
        JsonNode sent = mapper.readTree(received.get());
        assertThat(sent.get("model").asText()).isEqualTo("llama3.2:1b");
        assertThat(sent.get("stream").asBoolean()).isFalse();
        assertThat(sent.get("format").asText()).isEqualTo("json");
        assertThat(sent.get("prompt").asText()).isEqualTo("prompt");
        assertThat(sent.get("options").get("temperature").asDouble()).isZero();
        assertThat(sent.get("options").get("num_predict").asInt()).isEqualTo(64);
        assertThat(sent.get("options").get("num_ctx").asInt()).isEqualTo(2_048);
    }

    @Test
    void rejectsHttpErrors() throws Exception {
        start(exchange -> respond(exchange, 503, "{\"error\":\"busy\"}"));
        HttpOllamaClient client = new HttpOllamaClient(properties(port()), new ObjectMapper());

        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOf(OllamaException.class)
                .hasMessageContaining("503");
    }

    @Test
    void rejectsPayloadWithoutGeneratedResponse() throws Exception {
        start(exchange -> respond(exchange, 200, "{\"done\":true}"));
        HttpOllamaClient client = new HttpOllamaClient(properties(port()), new ObjectMapper());

        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOf(OllamaException.class);
    }

    @Test
    void rejectsMalformedPayload() throws Exception {
        start(exchange -> respond(exchange, 200, "not json"));
        HttpOllamaClient client = new HttpOllamaClient(properties(port()), new ObjectMapper());

        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOf(OllamaException.class);
    }

    private void start(Handler handler) throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/generate", handler::handle);
        server.start();
    }

    private int port() {
        return server.getAddress().getPort();
    }

    private static OllamaProperties properties(int port) {
        return new OllamaProperties(
                true,
                "http://127.0.0.1:" + port,
                "llama3.2:1b",
                5,
                12_000,
                64,
                2_048,
                "5m");
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
