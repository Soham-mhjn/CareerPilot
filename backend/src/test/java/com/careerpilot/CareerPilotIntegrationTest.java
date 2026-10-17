package com.careerpilot;

import com.careerpilot.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:careerpilottest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=", "careerpilot.storage.path=./target/test-resumes", "careerpilot.ai.provider=demo", "careerpilot.mail.enabled=true", "careerpilot.mail.from=noreply@example.test"})
@AutoConfigureMockMvc
class CareerPilotIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder passwordEncoder;
    @MockitoBean
    JavaMailSender mailSender;

    private MockHttpSession register(String name, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"correct-horse-42\"}")).andExpect(status().isCreated()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void userCanCreateAndTrackTheirApplicationsAndExport() throws Exception {
        MockHttpSession session = register("Ari User", "ari@example.test");
        String payload = "{\"company\":\"Northstar\",\"role\":\"Backend Engineer\",\"location\":\"Remote\",\"jobDescription\":\"Java Spring PostgreSQL APIs\",\"status\":\"APPLIED\",\"priority\":\"HIGH\"}";
        MvcResult created = mvc.perform(post("/api/v1/applications").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(patch("/api/v1/applications/{id}/status", id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INTERVIEW\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INTERVIEW"));
        mvc.perform(patch("/api/v1/applications/{id}/status", id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INTERVIEW\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/applications/{id}/status-history", id).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2))).andExpect(jsonPath("$[0].toStatus").value("APPLIED")).andExpect(jsonPath("$[1].fromStatus").value("APPLIED")).andExpect(jsonPath("$[1].toStatus").value("INTERVIEW"));
        mvc.perform(get("/api/v1/dashboard/summary").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.totalApplications").value(1)).andExpect(jsonPath("$.interviews").value(1));
        mvc.perform(get("/api/v1/export/applications.csv").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Northstar")));
    }

    @Test
    void recordsAreNotVisibleAcrossAccounts() throws Exception {
        MockHttpSession first = register("First User", "first@example.test");
        mvc.perform(post("/api/v1/applications").session(first).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"company\":\"SecretCo\",\"role\":\"Staff Engineer\"}")).andExpect(status().isCreated());
        MockHttpSession second = register("Second User", "second@example.test");
        mvc.perform(get("/api/v1/applications").session(second)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/applications/1").session(second)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/applications/1/status-history").session(second)).andExpect(status().isNotFound());
    }

    @Test
    void analyzerReturnsAndPersistsExplainableDeterministicScore() throws Exception {
        MockHttpSession session = register("Match User", "match@example.test");
        String body = "{\"company\":\"DataCo\",\"role\":\"Java Engineer\",\"jobDescription\":\"Java Spring PostgreSQL\"}";
        MvcResult app = mvc.perform(post("/api/v1/applications").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        long id = json.readTree(app.getResponse().getContentAsString()).get("id").asLong();
        MvcResult result = mvc.perform(post("/api/v1/applications/{id}/analyses", id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"resumeText\":\"Java and Spring developer\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("DEMO_DETERMINISTIC")).andExpect(jsonPath("$.score").isNumber()).andReturn();
        assertTrue(json.readTree(result.getResponse().getContentAsString()).get("score").asInt() > 0);
        mvc.perform(get("/api/v1/applications/{id}/analyses", id).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].score").isNumber());
    }

    @Test
    void rejectsInvalidApplicationStatus() throws Exception {
        MockHttpSession session = register("Status User", "status@example.test");
        MvcResult app = mvc.perform(post("/api/v1/applications").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"company\":\"Test\",\"role\":\"Engineer\"}")).andExpect(status().isCreated()).andReturn();
        long id = json.readTree(app.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(patch("/api/v1/applications/{id}/status", id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MAGIC\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/applications").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"company\":\"Test2\",\"role\":\"Engineer\",\"priority\":\"URGENT\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void analyticsSupportsDateRangesAndReportsCohortRates() throws Exception {
        MockHttpSession session = register("Analytics User", "analytics@example.test");
        mvc.perform(post("/api/v1/applications").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"company\":\"A\",\"role\":\"Engineer\",\"source\":\"Referral\",\"status\":\"OFFER\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/applications").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"company\":\"B\",\"role\":\"Designer\",\"status\":\"APPLIED\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/analytics/applications?range=all").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.progressed").value(1)).andExpect(jsonPath("$.resolved").value(1)).andExpect(jsonPath("$.positiveOutcomeRate").value(100)).andExpect(jsonPath("$.timeline[0].count").isNumber()).andExpect(jsonPath("$.transitionDistribution.APPLIED").value(1)).andExpect(jsonPath("$.transitionDistribution.OFFER").value(1)).andExpect(jsonPath("$.transitionTimeline[0].count").isNumber()).andExpect(jsonPath("$.averageDaysToInterview").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/v1/analytics/applications?range=30d").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.range").value("30d"));
        mvc.perform(get("/api/v1/analytics/applications?range=bad").session(session)).andExpect(status().isBadRequest());
    }

    @Test
    void aiStatusAndCareerInsightsAreAccountScopedAndIdentifyDemoMode() throws Exception {
        MockHttpSession session = register("AI User", "ai-status@example.test");
        mvc.perform(get("/api/v1/ai/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ai/status").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.provider").value("DEMO_DETERMINISTIC"));
        mvc.perform(get("/api/v1/ai/career-insights?range=30d").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("DEMO_DETERMINISTIC")).andExpect(jsonPath("$.insights[0]").isNotEmpty());
    }

    @Test
    void resumeUploadExtractsTextAndRemainsOwned() throws Exception {
        MockHttpSession session = register("Resume User", "resume@example.test");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/resumes").file(new org.springframework.mock.web.MockMultipartFile("file", "Alex_Resume.txt", "text/plain", "Java Spring developer with PostgreSQL skills".getBytes())).param("versionName", "Original").session(session).with(csrf())).andExpect(status().isCreated()).andExpect(jsonPath("$.extractedText").value(org.hamcrest.Matchers.containsString("Java Spring")));
        mvc.perform(get("/api/v1/resumes").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].fileName").value("Alex_Resume.txt"));
    }

    @Test
    void followUpTasksCanBeCreatedAndCompleted() throws Exception {
        MockHttpSession session = register("Task User", "task@example.test");
        MvcResult created = mvc.perform(post("/api/v1/workspace/items").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TASK\",\"title\":\"Send thank-you note\",\"company\":\"Northstar\"}")).andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/v1/workspace/items/{id}", id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"INTERVIEW\",\"title\":\"Updated interview\",\"company\":\"Northstar\",\"startsAt\":\"2026-11-22T10:30:00Z\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated interview")).andExpect(jsonPath("$.type").value("INTERVIEW"));
        mvc.perform(patch("/api/v1/workspace/items/{id}/complete", id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));
        mvc.perform(post("/api/v1/workspace/items").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"INTERVIEW\",\"title\":\"Interview\",\"durationMinutes\":1441}")).andExpect(status().isBadRequest());
    }

    @Test
    void interviewPracticeQuestionsArePersistedAndOwned() throws Exception {
        MockHttpSession session = register("Interview User", "interview@example.test");
        MvcResult created = mvc.perform(post("/api/v1/applications").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"company\":\"Orbit\",\"role\":\"Product Designer\"}")).andExpect(status().isCreated()).andReturn();
        long appId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/v1/applications/{id}/interview-questions", appId).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"difficulty\":\"MEDIUM\"}")).andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(5))).andExpect(jsonPath("$[0].question").isNotEmpty());
        mvc.perform(get("/api/v1/applications/{id}/interview-questions", appId).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(5)));
    }

    @Test
    void browserCsrfTokenHeaderSupportsRegistrationAndSessionLogin() throws Exception {
        MvcResult tokenResult = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        String token = json.readTree(tokenResult.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/v1/auth/register").session(session).header("X-CSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Browser User\",\"email\":\"browser@example.test\",\"password\":\"correct-horse-42\"}")).andExpect(status().isCreated());
    }

    @Test
    void profileCanBeUpdatedAndAccountDeletionInvalidatesSession() throws Exception {
        MockHttpSession session = register("Profile User", "profile@example.test");
        mvc.perform(put("/api/v1/profile").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Updated Name\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated Name"));
        mvc.perform(post("/api/v1/applications").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"company\":\"Private Co\",\"role\":\"Engineer\"}")).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/profile").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/applications").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetUsesSingleUseExpiringEmailToken() throws Exception {
        MockHttpSession session = register("Reset User", "reset@example.test");
        mvc.perform(post("/api/v1/auth/password-reset/request").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"reset@example.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").exists());
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        String resetLine = message.getValue().getText().lines().filter(line -> line.contains("?resetToken=")).findFirst().orElseThrow();
        String token = resetLine.substring(resetLine.indexOf("resetToken=") + 11);
        String confirm = "{\"token\":\"" + token + "\",\"newPassword\":\"fresh-password-99\"}";
        mvc.perform(post("/api/v1/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(confirm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Your password has been updated. You can sign in now."));
        mvc.perform(post("/api/v1/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(confirm)).andExpect(status().isBadRequest());
        assertTrue(passwordEncoder.matches("fresh-password-99", users.findByEmailIgnoreCase("reset@example.test").orElseThrow().passwordHash), "the new password hash should be saved");
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"reset@example.test\",\"password\":\"fresh-password-99\"}")).andExpect(status().isOk());
    }
}






