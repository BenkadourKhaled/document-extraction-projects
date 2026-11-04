package dz.DocuScan.document.exception;

import java.net.URI;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DocumentException.class)
    public ResponseEntity<ProblemDetail> handleDocument(DocumentException ex) {
        return build(HttpStatus.valueOf(ex.getStatus()), ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> tooLarge(Exception ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "La taille maximale autorisee est depassee.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " : " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST,
                detail.isBlank() ? "Requete invalide." : detail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Acces refuse.");
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> badRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Requete invalide.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> badPathParameter(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Identifiant invalide.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> internal(Exception ex) {
        log.error("Unhandled API error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur interne pendant le traitement de la requete.");
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, detail == null ? "Erreur" : detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://api.DocuScan.dz/problems/" + status.value()));
        return ResponseEntity.status(status).body(problem);
    }
}
