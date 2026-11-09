package dz.DocuScan.document.controller;

import dz.DocuScan.document.config.ExtractionProperties;
import dz.DocuScan.document.dto.BatchExtractionResponse;
import dz.DocuScan.document.dto.ExtractionResponse;
import dz.DocuScan.document.dto.ReprocessRequest;
import dz.DocuScan.document.exception.DocumentException;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.ExtractionStatus;
import dz.DocuScan.document.service.ExtractionExportService;
import dz.DocuScan.document.service.ExtractionService;
import dz.DocuScan.document.web.RequestInfo;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/extractions")
public class ExtractionController {

    private final ExtractionService service;
    private final ExtractionExportService exports;
    private final ExtractionProperties props;

    public ExtractionController(
            ExtractionService service,
            ExtractionExportService exports,
            ExtractionProperties props) {
        this.service = service;
        this.exports = exports;
        this.props = props;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(required = false) String languages,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        RequestInfo info = RequestInfo.from(request);

        if (files != null && !files.isEmpty()) {
            List<ExtractionResponse> responses = new ArrayList<>();
            for (MultipartFile f : files) {
                if (f == null || f.isEmpty()) {
                    throw new DocumentException(400, "Le fichier est vide.");
                }
                if (f.getSize() > props.maxFileSizeBytes()) {
                    throw new DocumentException(413, "La taille maximale autorisee est depassee.");
                }
                try {
                    ExtractionJob job = service.create(
                            f.getOriginalFilename(),
                            f.getBytes(),
                            languages,
                            user,
                            info.ipAddress(),
                            info.userAgent());
                    responses.add(service.response(job));
                } catch (DocumentException e) {
                    throw e;
                } catch (Exception e) {
                    throw new DocumentException(422, "Impossible de traiter le document.", e);
                }
            }
            return ResponseEntity.accepted().body(new BatchExtractionResponse(responses));
        }

        if (file == null) {
            throw new DocumentException(400, "Aucun fichier fourni.");
        }
        if (file.isEmpty()) throw new DocumentException(400, "Le fichier est vide.");
        if (file.getSize() > props.maxFileSizeBytes()) {
            throw new DocumentException(413, "La taille maximale autorisee est depassee.");
        }
        try {
            ExtractionJob job = service.create(
                    file.getOriginalFilename(),
                    file.getBytes(),
                    languages,
                    user,
                    info.ipAddress(),
                    info.userAgent());
            return ResponseEntity.accepted().body(service.response(job));
        } catch (DocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentException(422, "Impossible de traiter le document.", e);
        }
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<ExtractionResponse> reprocess(
            @PathVariable UUID id,
            @Valid @RequestBody ReprocessRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestInfo info = RequestInfo.from(httpRequest);
        String languages = String.join("+", request.languages());
        ExtractionJob job = service.reprocess(id, languages, user, info.ipAddress(), info.userAgent());
        return ResponseEntity.accepted().body(service.response(job));
    }

    @GetMapping("/{id}")
    public ExtractionResponse get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.response(service.get(id, user));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        ExtractionService.FileContent content = service.loadFile(id, user);
        MediaType type;
        try {
            type = MediaType.parseMediaType(content.mimeType());
        } catch (RuntimeException ex) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + content.fileName().replace("\"", "") + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content.content());
    }

    @GetMapping("/{id}/download")
    @Operation(
            summary = "Download the extraction as TXT or JSON",
            description = "TXT keeps the full text then appends the effective field values. JSON keeps "
                    + "the existing keys and adds schema_version and fields. Both include corrections.")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "txt") String format,
            @AuthenticationPrincipal AuthenticatedUser user) {
        ExtractionJob job = service.get(id, user);
        if (job.getStatus() != ExtractionStatus.COMPLETED) {
            throw new DocumentException(409, "Le resultat n'est pas encore disponible.");
        }

        byte[] bytes;
        MediaType type;
        String extension;

        if ("txt".equalsIgnoreCase(format)) {
            bytes = exports.txtBytes(job);
            type = MediaType.TEXT_PLAIN;
            extension = "txt";
        } else if ("json".equalsIgnoreCase(format)) {
            bytes = exports.jsonBytes(job);
            type = MediaType.APPLICATION_JSON;
            extension = "json";
        } else {
            throw new DocumentException(400, "Format invalide. Utilisez txt ou json.");
        }

        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"extraction-" + id + "." + extension + "\"")
                .body(bytes);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        RequestInfo info = RequestInfo.from(request);
        service.delete(id, user, info.ipAddress(), info.userAgent());
        return ResponseEntity.noContent().build();
    }
}
