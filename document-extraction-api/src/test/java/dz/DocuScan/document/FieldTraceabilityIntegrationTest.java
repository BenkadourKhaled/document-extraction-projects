package dz.DocuScan.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dz.DocuScan.document.extractor.PdfTextExtractorTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end traceability scenario on a real queued worker:
 * upload, field extraction with source spans, append-only corrections,
 * re-extraction in the same lineage, and both export paths.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:field-traceability-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.storage.local-path=${java.io.tmpdir}/document-extraction-field-traceability-test",
        "app.queue.worker-enabled=true",
        "app.queue.poll-interval-ms=100"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FieldTraceabilityIntegrationTest {

    private static final String ARABIC_CORRECTION =
            "FACT-\u0662\u0660\u0662\u0666-\u0667\u0667\u0668";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void keepsSourceAndLineageWideCorrectionsAcrossReprocessAndExports() throws Exception {
        String admin = login("admin@test.local", "TestPassword123!");

        String otherEmail = "field-user-" + UUID.randomUUID().toString().replace("-", "") + "@test.local";
        createUser(admin, otherEmail, "FieldUserPass123!", "USER");
        String otherUser = login(otherEmail, "FieldUserPass123!");

        byte[] pdf = PdfTextExtractorTest.pdfWithLines(
                "Facture numero 2026-001",
                "Date: 31/01/2026");

        String originalId = upload(admin, pdf);
        awaitCompleted(admin, originalId);

        JsonNode fields = getFields(admin, originalId);
        assertThat(fields.size()).isEqualTo(2);

        JsonNode reference = field(fields, "reference_number");
        String originalFieldId = reference.get("id").asText();
        String lineageId = reference.get("lineageId").asText();
        assertThat(lineageId).isEqualTo(originalId);
        assertThat(reference.get("rawValue").asText()).isEqualTo("2026-001");
        assertThat(reference.get("normalizedValue").asText()).isEqualTo("2026-001");
        assertThat(reference.get("effectiveValue").asText()).isEqualTo("2026-001");
        assertThat(reference.get("verificationStatus").asText()).isEqualTo("VERIFIED");
        assertThat(reference.get("corrected").asBoolean()).isFalse();
        assertThat(reference.get("corrections")).isEmpty();

        JsonNode referenceSource = reference.get("sources").get(0);
        assertThat(referenceSource.get("pageNumber").asInt()).isEqualTo(1);
        assertThat(referenceSource.get("sourceSpace").asText()).isEqualTo("PDF_POINTS");
        assertThat(referenceSource.get("pageWidth").asDouble()).isEqualTo(612.0);
        assertThat(referenceSource.get("pageHeight").asDouble()).isEqualTo(792.0);
        assertThat(referenceSource.get("rotationDeg").asInt()).isZero();
        assertThat(referenceSource.get("sourceText").asText())
                .contains("Facture numero 2026-001");
        assertThat(referenceSource.get("charEnd").asInt())
                .isGreaterThan(referenceSource.get("charStart").asInt());
        assertBbox(referenceSource);

        JsonNode date = field(fields, "document_date");
        assertThat(date.get("rawValue").asText()).isEqualTo("31/01/2026");
        assertThat(date.get("normalizedValue").asText()).isEqualTo("2026-01-31");
        assertThat(date.get("verificationStatus").asText()).isEqualTo("VERIFIED");
        assertThat(date.get("sources").get(0).get("sourceText").asText())
                .contains("Date: 31/01/2026");

        // Access rules: a non-owner USER cannot read or correct the document,
        // and anonymous calls never reach the field endpoints.
        mvc.perform(get("/api/v1/extractions/{id}/fields", originalId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUser))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/extractions/{id}/fields/{key}/corrections",
                                originalId, "reference_number")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"SHOULD-NOT-BE-STORED"}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/extractions/{id}/fields", originalId))
                .andExpect(status().isUnauthorized());

        JsonNode first = correct(
                admin, originalId, "reference_number", "FACT-2026-777", "review");
        assertThat(first.get("corrected").asBoolean()).isTrue();
        assertThat(first.get("rawValue").asText()).isEqualTo("2026-001");
        assertThat(first.get("effectiveValue").asText()).isEqualTo("FACT-2026-777");
        assertThat(first.get("corrections").size()).isEqualTo(1);
        assertThat(first.get("corrections").get(0).get("active").asBoolean()).isTrue();
        assertThat(first.get("corrections").get(0).get("previousValue").asText())
                .isEqualTo("2026-001");
        assertThat(first.get("corrections").get(0).get("supersededAt").isNull()).isTrue();

        JsonNode second = correct(
                admin, originalId, "reference_number", ARABIC_CORRECTION, "arabic digits");
        assertThat(second.get("rawValue").asText()).isEqualTo("2026-001");
        assertThat(second.get("normalizedValue").asText()).isEqualTo("2026-001");
        assertThat(second.get("effectiveValue").asText()).isEqualTo(ARABIC_CORRECTION);
        assertThat(second.get("corrected").asBoolean()).isTrue();
        assertThat(second.get("corrections").size()).isEqualTo(2);

        JsonNode active = findCorrection(second.get("corrections"), true);
        JsonNode superseded = findCorrection(second.get("corrections"), false);
        assertThat(active.get("correctedValue").asText()).isEqualTo(ARABIC_CORRECTION);
        assertThat(active.get("previousValue").asText()).isEqualTo("FACT-2026-777");
        assertThat(active.get("supersededAt").isNull()).isTrue();
        assertThat(superseded.get("correctedValue").asText()).isEqualTo("FACT-2026-777");
        assertThat(superseded.get("previousValue").asText()).isEqualTo("2026-001");
        assertThat(superseded.get("supersededAt").isNull()).isFalse();

        String reprocessedId = reprocess(admin, originalId);
        assertThat(reprocessedId).isNotEqualTo(originalId);
        awaitCompleted(admin, reprocessedId);

        JsonNode reprocessedFields = getFields(admin, reprocessedId);
        assertThat(reprocessedFields.size()).isEqualTo(2);
        JsonNode reprocessedReference = field(reprocessedFields, "reference_number");
        assertThat(reprocessedReference.get("id").asText()).isNotEqualTo(originalFieldId);
        assertThat(reprocessedReference.get("lineageId").asText()).isEqualTo(lineageId);
        assertThat(reprocessedReference.get("rawValue").asText()).isEqualTo("2026-001");
        assertThat(reprocessedReference.get("effectiveValue").asText())
                .isEqualTo(ARABIC_CORRECTION);
        assertThat(reprocessedReference.get("corrected").asBoolean()).isTrue();
        assertThat(reprocessedReference.get("sources")).isNotEmpty();
        assertThat(reprocessedReference.get("corrections").size()).isEqualTo(2);
        assertBbox(reprocessedReference.get("sources").get(0));

        // The correction is lineage-wide: the original extraction now exposes it too.
        JsonNode originalAfterReprocess = field(getFields(admin, originalId), "reference_number");
        assertThat(originalAfterReprocess.get("effectiveValue").asText())
                .isEqualTo(ARABIC_CORRECTION);

        String txt = new String(
                mvc.perform(get("/api/v1/extractions/{id}/download", reprocessedId)
                                .param("format", "txt")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertThat(txt)
                .contains("Facture numero 2026-001")
                .contains("Date: 31/01/2026")
                .contains("--- Extracted fields (effective values) ---")
                .contains(ARABIC_CORRECTION)
                .contains("[VERIFIED, CORRECTED]");

        byte[] jsonBytes = mvc.perform(get("/api/v1/extractions/{id}/download", reprocessedId)
                        .param("format", "json")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        JsonNode exported = objectMapper.readTree(jsonBytes);
        assertThat(exported.get("schema_version").asText()).isEqualTo("field-traceability-v1");
        assertThat(exported.get("id").asText()).isEqualTo(reprocessedId);
        assertThat(exported.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(exported.get("fullText").asText()).contains("Facture numero 2026-001");
        assertThat(exported.get("pages")).isNotEmpty();

        JsonNode exportedFields = exported.get("fields");
        assertThat(exportedFields.size()).isEqualTo(2);
        JsonNode exportedReference = exportField(exportedFields, "reference_number");
        assertThat(exportedReference.get("raw_value").asText()).isEqualTo("2026-001");
        assertThat(exportedReference.get("normalized_value").asText()).isEqualTo("2026-001");
        assertThat(exportedReference.get("effective_value").asText())
                .isEqualTo(ARABIC_CORRECTION);
        assertThat(exportedReference.get("verification_status").asText()).isEqualTo("VERIFIED");
        assertThat(exportedReference.get("extractor_version").asText())
                .isEqualTo("rules-reference-v1");
        assertThat(exportedReference.get("sources").get(0).get("source_space").asText())
                .isEqualTo("PDF_POINTS");
        assertThat(exportedReference.get("sources").get(0).get("page_number").asInt())
                .isEqualTo(1);
        assertThat(exportedReference.get("corrections").size()).isEqualTo(2);

        byte[] zipBytes = mvc.perform(post("/api/v1/documents/zip")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":["%s"],"format":"json"}
                                """.formatted(reprocessedId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        Map<String, byte[]> entries = unzip(zipBytes);
        assertThat(entries).hasSize(1);
        String entryName = entries.keySet().iterator().next();
        assertThat(entryName).endsWith(".json");
        JsonNode zipped = objectMapper.readTree(entries.get(entryName));
        assertThat(zipped.get("schema_version").asText()).isEqualTo("field-traceability-v1");
        JsonNode zippedReference = exportField(zipped.get("fields"), "reference_number");
        assertThat(zippedReference.get("effective_value").asText())
                .isEqualTo(ARABIC_CORRECTION);
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private void createUser(String adminToken, String email, String password, String role)
            throws Exception {
        mvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"Field Test","password":"%s","role":"%s"}
                                """.formatted(email, password, role)))
                .andExpect(status().isCreated());
    }

    private String upload(String token, byte[] pdf) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "traceability.pdf", MediaType.APPLICATION_PDF_VALUE, pdf);
        String body = mvc.perform(multipart("/api/v1/extractions")
                        .file(file)
                        .param("languages", "fra+eng")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String reprocess(String token, String documentId) throws Exception {
        String body = mvc.perform(post("/api/v1/extractions/{id}/reprocess", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"languages":["fra","eng"]}
                                """))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private JsonNode awaitCompleted(String token, String documentId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        JsonNode result = null;
        while (System.nanoTime() < deadline) {
            byte[] body = mvc.perform(get("/api/v1/extractions/{id}", documentId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray();
            result = objectMapper.readTree(body);
            String status = result.get("status").asText();
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(result).isNotNull();
        assertThat(result.get("status").asText())
                .as("extraction result: %s", result)
                .isEqualTo("COMPLETED");
        return result;
    }

    private JsonNode getFields(String token, String documentId) throws Exception {
        byte[] body = mvc.perform(get("/api/v1/extractions/{id}/fields", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        return objectMapper.readTree(body);
    }

    private JsonNode correct(
            String token,
            String documentId,
            String fieldKey,
            String value,
            String reason) throws Exception {
        byte[] body = mvc.perform(post(
                                "/api/v1/extractions/{id}/fields/{key}/corrections",
                                documentId, fieldKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("value", value, "reason", reason))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        return objectMapper.readTree(body);
    }

    private JsonNode field(JsonNode fields, String fieldKey) {
        for (JsonNode field : fields) {
            if (fieldKey.equals(field.get("fieldKey").asText())) {
                return field;
            }
        }
        throw new AssertionError("Missing field " + fieldKey + " in " + fields);
    }

    private JsonNode exportField(JsonNode fields, String fieldKey) {
        for (JsonNode field : fields) {
            if (fieldKey.equals(field.get("field_key").asText())) {
                return field;
            }
        }
        throw new AssertionError("Missing exported field " + fieldKey + " in " + fields);
    }

    private JsonNode findCorrection(JsonNode corrections, boolean active) {
        for (JsonNode correction : corrections) {
            if (correction.get("active").asBoolean() == active) {
                return correction;
            }
        }
        throw new AssertionError("Missing correction with active=" + active + " in " + corrections);
    }

    private void assertBbox(JsonNode source) {
        double x0 = source.get("bboxX0").asDouble();
        double y0 = source.get("bboxY0").asDouble();
        double x1 = source.get("bboxX1").asDouble();
        double y1 = source.get("bboxY1").asDouble();
        assertThat(x0).isBetween(0.0, 1.0);
        assertThat(y0).isBetween(0.0, 1.0);
        assertThat(x1).isBetween(0.0, 1.0);
        assertThat(y1).isBetween(0.0, 1.0);
        assertThat(x1).isGreaterThan(x0);
        assertThat(y1).isGreaterThan(y0);
    }

    private Map<String, byte[]> unzip(byte[] zipBytes) throws Exception {
        Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
                zip.closeEntry();
            }
        }
        return entries;
    }
}
