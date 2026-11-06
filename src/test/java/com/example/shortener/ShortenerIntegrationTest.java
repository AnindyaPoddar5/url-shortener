package com.example.shortener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:tests;DB_CLOSE_DELAY=-1",
        "app.api-key=integration-test-key-at-least-24", "app.requests-per-minute=10000"})
@AutoConfigureMockMvc
class ShortenerIntegrationTest {
    static final String KEY = "integration-test-key-at-least-24";
    static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired LinkRepository repository;
    @Autowired LinkService service;
    @MockitoBean Clock clock;
    @MockitoSpyBean CodeGenerator generator;
    @BeforeEach void resetState() {
        repository.deleteAll(); when(clock.instant()).thenReturn(NOW); when(clock.millis()).thenReturn(NOW.toEpochMilli());
    }
    void createAlias(String code) throws Exception {
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com/a?x=1\",\"customAlias\":\"" + code + "\"}"))
            .andExpect(status().isCreated());
    }
    @Test void createRedirectAndAnalytics() throws Exception {
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com/a?x=1\",\"customAlias\":\"demo123\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/links/demo123"))
            .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/s/demo123"));
        mvc.perform(get("/s/demo123")).andExpect(status().isFound())
            .andExpect(header().string("Location", "https://example.com/a?x=1"))
            .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/links/demo123").header("X-API-Key", KEY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.clicks").value(1))
            .andExpect(jsonPath("$.lastAccessedAt").value(NOW.toString()));
    }
    @Test void automaticCode() throws Exception {
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("[a-zA-Z0-9]{8}")));
    }
    @Test void aliasConflictDoesNotOverwrite() throws Exception {
        createAlias("same123");
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://other.example\",\"customAlias\":\"same123\"}"))
            .andExpect(status().isConflict());
        assertThat(service.get("same123").getTargetUrl()).isEqualTo("https://example.com/a?x=1");
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "a", "abc", "123456789012345678901234567890123"})
    void invalidAliasLengthsAreRejectedWithoutCreatingLink(String alias) throws Exception {
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com\",\"customAlias\":\"" + alias + "\"}"))
            .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }
    @ParameterizedTest
    @ValueSource(strings = {"demo name", "demo.name", "demo/name", "caf\u00e912"})
    void invalidAliasCharactersAreRejectedWithoutCreatingLink(String alias) throws Exception {
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com\",\"customAlias\":\"" + alias + "\"}"))
            .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }
    @ParameterizedTest
    @ValueSource(strings = {"A0_-", "Ab0123456789012345678901234567_-"})
    void validAliasBoundariesArePreservedAndResolve(String alias) throws Exception {
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com\",\"customAlias\":\"" + alias + "\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(alias))
            .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/s/" + alias));
        mvc.perform(get("/s/" + alias)).andExpect(status().isFound())
            .andExpect(header().string("Location", "https://example.com"));
    }
    @Test void aliasesDifferingOnlyByCaseHaveIndependentDestinations() throws Exception {
        createAlias("Demo123");
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://other.example\",\"customAlias\":\"demo123\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("demo123"));
        mvc.perform(get("/s/Demo123")).andExpect(status().isFound())
            .andExpect(header().string("Location", "https://example.com/a?x=1"));
        mvc.perform(get("/s/demo123")).andExpect(status().isFound())
            .andExpect(header().string("Location", "https://other.example"));
        mvc.perform(get("/s/DEMO123")).andExpect(status().isNotFound());
        assertThat(repository.count()).isEqualTo(2);
    }
    @Test void generatedCollisionRetriesFreshTransaction() {
        service.create("https://example.com", "repeat12", null);
        doReturn("repeat12", "unique12").when(generator).next();
        assertThat(service.create("https://other.example", null, null).getCode()).isEqualTo("unique12");
        assertThat(repository.count()).isEqualTo(2);
    }
    @Test void collisionRetryIsBounded() {
        service.create("https://example.com", "repeat12", null);
        doReturn("repeat12").when(generator).next();
        assertThatThrownBy(() -> service.create("https://other.example", null, null))
            .isInstanceOf(ApiException.class).hasMessageContaining("Unable to allocate");
    }
    @Test void missingLink() throws Exception {
        mvc.perform(get("/s/missing")).andExpect(status().isNotFound());
    }
    @Test void unknownAnalytics() throws Exception {
        mvc.perform(get("/api/v1/links/missing").header("X-API-Key", KEY)).andExpect(status().isNotFound());
    }
    @Test void expirationBoundaryDoesNotCount() throws Exception {
        service.create("https://example.com", "expiry12", NOW.plusSeconds(60));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        mvc.perform(get("/s/expiry12")).andExpect(status().isGone());
        assertThat(service.get("expiry12").getClicks()).isZero();
    }
    @Test void headDoesNotCount() throws Exception {
        createAlias("head123");
        mvc.perform(head("/s/head123")).andExpect(status().isFound());
        assertThat(service.get("head123").getClicks()).isZero();
    }
    @Test void expiryCanBeChangedAndRemovedWithoutResettingClicks() throws Exception {
        createAlias("update12"); service.resolve("update12", true);
        mvc.perform(patch("/api/v1/links/update12/expiration").header("X-API-Key", KEY)
            .contentType(MediaType.APPLICATION_JSON).content("{\"expiresAt\":\"2026-11-06T13:00:00Z\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.clicks").value(1));
        mvc.perform(patch("/api/v1/links/update12/expiration").header("X-API-Key", KEY)
            .contentType(MediaType.APPLICATION_JSON).content("{\"expiresAt\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").doesNotExist());
        assertThat(service.get("update12").getClicks()).isEqualTo(1);
    }
    @Test void pastExpiryRejected() throws Exception {
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void malformedBody() throws Exception {
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{invalid")) .andExpect(status().isBadRequest());
    }
    @Test void blankUrl() throws Exception {
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"\"}")) .andExpect(status().isBadRequest());
    }
    @Test void managementRequiresKey() throws Exception {
        mvc.perform(get("/api/v1/links/demo123")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/links/demo123").header("X-API-Key", "wrong")) .andExpect(status().isUnauthorized());
    }
    @Test void oversizedBodyRejected() throws Exception {
        mvc.perform(post("/api/v1/links").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
            .content("x".repeat(16385))).andExpect(status().isPayloadTooLarge());
    }
    @Test void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
    @Test void disableIsIdempotentAndPreservesAnalytics() throws Exception {
        createAlias("disable1"); service.resolve("disable1", true);
        for (int i = 0; i < 2; i++) mvc.perform(delete("/api/v1/links/disable1").header("X-API-Key", KEY))
            .andExpect(status().isNoContent());
        mvc.perform(get("/s/disable1")).andExpect(status().isGone());
        mvc.perform(head("/s/disable1")).andExpect(status().isGone());
        mvc.perform(get("/api/v1/links/disable1").header("X-API-Key", KEY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.clicks").value(1))
            .andExpect(jsonPath("$.enabled").value(false));
        assertThatThrownBy(() -> service.create("https://example.com", "disable1", null))
            .isInstanceOf(ApiException.class).hasMessageContaining("already exists");
    }
    @Test void disablingMissingLinkReturns404() throws Exception {
        mvc.perform(delete("/api/v1/links/missing").header("X-API-Key", KEY)).andExpect(status().isNotFound());
    }
    @Test void concurrentClicksAreNotLost() throws Exception {
        service.create("https://example.com", "parallel", null);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<Callable<String>>();
            for (int i = 0; i < 100; i++) tasks.add(() -> service.resolve("parallel", true));
            for (var result : pool.invokeAll(tasks)) assertThat(result.get()).isEqualTo("https://example.com");
        }
        assertThat(service.get("parallel").getClicks()).isEqualTo(100);
    }
    @Test void concurrentAliasClaimsHaveOneWinner() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 8; i++) tasks.add(() -> {
                try { service.create("https://example.com", "race1234", null); return true; }
                catch (ApiException ex) { assertThat(ex.status().value()).isEqualTo(409); return false; }
            });
            int successes = 0;
            for (var result : pool.invokeAll(tasks)) if (result.get()) successes++;
            assertThat(successes).isEqualTo(1);
        }
    }
}
