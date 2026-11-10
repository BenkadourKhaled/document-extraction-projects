package dz.DocuScan.document.ai;

/**
 * Technical failure while calling the local Ollama endpoint.
 *
 * <p>Messages deliberately contain only transport/status information: prompts,
 * model responses and extracted field values are never included.</p>
 */
public class OllamaException extends RuntimeException {

    public OllamaException(String message) {
        super(message);
    }

    public OllamaException(String message, Throwable cause) {
        super(message, cause);
    }
}
