package dz.DocuScan.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagementIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void adminCreatesUsersAndUsersCanChangeTheirPassword() throws Exception {
        String adminToken = login("admin@test.local", "TestPassword123!");
        String email = "reviewer-" + UUID.randomUUID().toString().replace("-", "") + "@test.local";

        String createdBody = mvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"Reviewer Test","password":"ReviewerPass123!","role":"REVIEWER"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("REVIEWER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String reviewerId = objectMapper.readTree(createdBody).get("id").asText();

        mvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + email + "')]").exists());

        String reviewerToken = login(email, "ReviewerPass123!");

        mvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"ReviewerPass123!","newPassword":"ReviewerPass456!"}
                                """))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ReviewerPass123!"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());

        login(email, "ReviewerPass456!");

        mvc.perform(patch("/api/v1/users/{id}", reviewerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"USER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        JsonNode admin = objectMapper.readTree(
                mvc.perform(get("/api/v1/auth/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());

        mvc.perform(patch("/api/v1/users/{id}", admin.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false}
                                """))
                .andExpect(status().isBadRequest());
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
}
