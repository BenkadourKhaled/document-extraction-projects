package dz.DocuScan.document.ai;

/** Minimal local Ollama client used by the optional field extractor. */
public interface OllamaClient {

    /**
     * Sends a non-streaming generation request and returns the raw text
     * produced by the model.
     *
     * @throws OllamaException when the endpoint is unavailable or returns an
     *         unusable response
     */
    String generate(String prompt);
}
