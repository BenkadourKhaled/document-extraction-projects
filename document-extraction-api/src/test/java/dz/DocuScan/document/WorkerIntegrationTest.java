package dz.DocuScan.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dz.DocuScan.document.extractor.PdfTextExtractorTest;
import java.time.Duration;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:worker-integration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.storage.local-path=${java.io.tmpdir}/document-extraction-worker-test",
        "app.queue.worker-enabled=true",
        "app.queue.poll-interval-ms=100"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void workerProcessesQueuedDocumentUntilCompletion() throws Exception {
        String loginBody = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(loginBody).get("accessToken").asText();

        MockMultipartFile file = new MockMultipartFile(
                "file", "worker.pdf", MediaType.APPLICATION_PDF_VALUE,
                PdfTextExtractorTest.pdfWithText("Worker integration document"));

        String createdBody = mvc.perform(multipart("/api/v1/extractions")
                        .file(file)
                        .param("languages", "fra+eng")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String documentId = objectMapper.readTree(createdBody).get("id").asText();
        JsonNode result = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();

        while (System.nanoTime() < deadline) {
            String body = mvc.perform(get("/api/v1/extractions/{id}", documentId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            result = objectMapper.readTree(body);
            String status = result.get("status").asText();
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                break;
            }
            Thread.sleep(200);
        }

        assertThat(result).isNotNull();
        assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.get("fullText").asText()).contains("Worker integration document");
    }
}
