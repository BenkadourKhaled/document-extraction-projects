package dz.DocuScan.document.exception;
public class DocumentException extends RuntimeException {
    private final int status;
    public DocumentException(int status, String message) { super(message); this.status = status; }
    public DocumentException(int status, String message, Throwable cause) { super(message, cause); this.status = status; }
    public int getStatus() { return status; }
}
