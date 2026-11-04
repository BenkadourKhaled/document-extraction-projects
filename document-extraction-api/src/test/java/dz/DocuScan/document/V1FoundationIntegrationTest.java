package dz.DocuScan.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dz.DocuScan.document.extractor.PdfTextExtractorTest;
import dz.DocuScan.document.repository.JobRepository;
import java.time.Instant;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class V1FoundationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JobRepository jobs;

    @Test
    void authenticatedDocumentLifecycleIsPersistedAndAudited() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.local","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());

        String loginBody = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("admin@test.local"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode login = objectMapper.readTree(loginBody);
        String token = login.get("accessToken").asText();
        assertThat(token).isNotBlank();

        mvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.local"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "integration.pdf", MediaType.APPLICATION_PDF_VALUE,
                PdfTextExtractorTest.pdfWithText("V1 integration document"));

        String createdBody = mvc.perform(multipart("/api/v1/extractions")
                        .file(file)
                        .param("languages", "fra")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.fileName").value("integration.pdf"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String documentId = objectMapper.readTree(createdBody).get("id").asText();

        mvc.perform(get("/api/v1/extractions/{id}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId))
                .andExpect(jsonPath("$.languages").value("fra"));

        mvc.perform(get("/api/v1/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].fileName").value("integration.pdf"));

        assertThat(jobs.claimNext("integration-test-worker", Instant.now())).isPresent();

        mvc.perform(get("/api/v1/extractions/{id}/file", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());

        mvc.perform(delete("/api/v1/extractions/{id}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/extractions/{id}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'AUTH_LOGIN')]").exists())
                .andExpect(jsonPath("$[?(@.action == 'DOCUMENT_CREATED')]").exists())
                .andExpect(jsonPath("$[?(@.action == 'DOCUMENT_DELETED')]").exists());
    }
}
