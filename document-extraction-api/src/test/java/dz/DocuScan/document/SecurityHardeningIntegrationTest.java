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
class SecurityHardeningIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void passwordChangeAndLogoutRevokeExistingTokens() throws Exception {
        String adminToken = login("admin@test.local", "TestPassword123!", "10.10.10.1");
        String email = uniqueEmail("revoke");
        String userId = createUser(adminToken, email, "ReviewerPass123!", "REVIEWER");

        String oldToken = login(email, "ReviewerPass123!", "10.10.10.2");

        mvc.perform(post("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"ReviewerPass123!","newPassword":"ReviewerPass456!"}
                                """))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        String newToken = login(email, "ReviewerPass456!", "10.10.10.2");

        mvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + userId + "')]").exists());
    }

    @Test
    void adminCanResetPasswordAndTargetTokensAreRevoked() throws Exception {
        String adminToken = login("admin@test.local", "TestPassword123!", "10.10.11.1");
        String email = uniqueEmail("reset");
        String userId = createUser(adminToken, email, "ReviewerPass123!", "USER");
        String targetToken = login(email, "ReviewerPass123!", "10.10.11.2");

        mvc.perform(post("/api/v1/users/{id}/password", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"AdminResetPass456!"}
                                """))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ReviewerPass123!"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());

        login(email, "AdminResetPass456!", "10.10.11.3");
    }

    @Test
    void userCanUpdateProfileAndEmailMustRemainUnique() throws Exception {
        String adminToken = login("admin@test.local", "TestPassword123!", "10.10.12.1");
        String email = uniqueEmail("profile");
        createUser(adminToken, email, "ReviewerPass123!", "USER");
        String token = login(email, "ReviewerPass123!", "10.10.12.2");

        mvc.perform(patch("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Profil Modifie","email":"%s"}
                                """.formatted(uniqueEmail("profile-updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Profil Modifie"));

        mvc.perform(patch("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Autre","email":"admin@test.local"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void repeatedFailedLoginsAreThrottled() throws Exception {
        String forwardedFor = "10.99." + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", forwardedFor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"admin@test.local","password":"wrong-password"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", forwardedFor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void passwordPolicyRejectsShortAccountAndChangePasswords() throws Exception {
        String adminToken = login("admin@test.local", "TestPassword123!", "10.10.13.1");

        mvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"short-%s@test.local","displayName":"Short",
                                 "password":"ShortPass1!","role":"USER"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"TestPassword123!","newPassword":"ShortPass1!"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private String createUser(String adminToken, String email, String password, String role)
            throws Exception {
        String body = mvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"Security Test","password":"%s","role":"%s"}
                                """.formatted(email, password, role)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String login(String email, String password, String forwardedFor) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", forwardedFor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        return response.get("accessToken").asText();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "") + "@test.local";
    }
}
