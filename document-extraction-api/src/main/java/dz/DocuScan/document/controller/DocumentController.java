package dz.DocuScan.document.controller;

import dz.DocuScan.document.dto.DocumentSummary;
import dz.DocuScan.document.dto.PageResponse;
import dz.DocuScan.document.dto.ZipDownloadRequest;
import dz.DocuScan.document.exception.DocumentException;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.model.ExtractionStatus;
import dz.DocuScan.document.service.ExtractionExportService;
import dz.DocuScan.document.service.ExtractionService;
import dz.DocuScan.document.web.RequestInfo;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final ExtractionService service;
    private final ExtractionExportService exports;

    public DocumentController(ExtractionService service, ExtractionExportService exports) {
        this.service = service;
        this.exports = exports;
    }

    @GetMapping
    public PageResponse<DocumentSummary> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(user, query, status, page, size, from, to);
    }

    @PostMapping("/zip")
    @Operation(
            summary = "Download several extractions as a ZIP archive",
            description = "Each entry is produced by the same export service as the single-document "
                    + "download, so effective field values and corrections are included.")
    public ResponseEntity<byte[]> zip(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ZipDownloadRequest request) {
        String format = request.format() == null ? "txt" : request.format().trim().toLowerCase(Locale.ROOT);
        if (!"txt".equals(format) && !"json".equals(format)) {
            throw new DocumentException(400, "Format invalide. Utilisez txt ou json.");
        }
        if (request.ids() == null || request.ids().isEmpty()) {
            throw new DocumentException(400, "Aucun document sélectionné.");
        }

        List<UUID> ids = request.ids().stream().distinct().toList();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Set<UUID> seen = java.util.HashSet.newHashSet(ids.size());
            for (UUID id : ids) {
                if (!seen.add(id)) {
                    continue;
                }
                try {
                    var job = service.get(id, user);
                    if (job.getStatus() != ExtractionStatus.COMPLETED) {
                        continue;
                    }
                    byte[] content;
                    String extension;
                    if ("json".equals(format)) {
                        content = exports.jsonBytes(job);
                        extension = "json";
                    } else {
                        content = exports.txtBytes(job);
                        extension = "txt";
                    }
                    String baseName = (job.getFileName() == null ? id.toString() : job.getFileName())
                            .replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
                    if (baseName.toLowerCase(Locale.ROOT).endsWith(".pdf")
                            || baseName.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg|tif|tiff|bmp|webp)$")) {
                        int dot = baseName.lastIndexOf('.');
                        if (dot > 0) {
                            baseName = baseName.substring(0, dot);
                        }
                    }
                    String entryName = baseName + "-" + id + "." + extension;
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.write(content);
                    zos.closeEntry();
                } catch (DocumentException ex) {
                    // Skip documents the user cannot access or missing ones
                    if (ex.getStatus() != 404) {
                        throw ex;
                    }
                }
            }
        } catch (IOException ex) {
            throw new DocumentException(500, "Impossible de générer le ZIP.", ex);
        }

        byte[] zipBytes = baos.toByteArray();
        if (zipBytes.length == 0) {
            throw new DocumentException(409, "Aucun document téléchargeable dans la sélection.");
        }
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"extractions-" + timestamp + ".zip\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(zipBytes);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        RequestInfo info = RequestInfo.from(request);
        service.clear(user, status, info.ipAddress(), info.userAgent());
        return ResponseEntity.noContent().build();
    }
}
