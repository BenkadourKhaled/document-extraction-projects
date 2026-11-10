package dz.DocuScan.document.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local Ollama configuration.
 *
 * <p>The feature is disabled by default so tests, CI and production
 * deployments never depend on a running model unless the operator explicitly
 * opts in. No secret is required: Ollama is expected to run on the local host
 * or on a trusted private network.</p>
 */
@ConfigurationProperties(prefix = "app.ai.ollama")
public record OllamaProperties(
        boolean enabled,
        String baseUrl,
        String model,
        int timeoutSeconds,
        int maxInputCharacters,
        int numPredict,
        int numContext,
        String keepAlive) {

    public OllamaProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        model = blankToDefault(model, "llama3.2:1b");
        timeoutSeconds = timeoutSeconds <= 0 ? 120 : timeoutSeconds;
        maxInputCharacters = maxInputCharacters <= 0
                ? 12_000
                : Math.max(1_000, maxInputCharacters);
        numPredict = numPredict <= 0 ? 256 : Math.max(16, numPredict);
        numContext = numContext <= 0 ? 8_192 : Math.max(512, numContext);
        keepAlive = blankToDefault(keepAlive, "5m");
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = blankToDefault(value, "http://localhost:11434").strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
