package io.quarkus.registry.app;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import io.quarkus.registry.app.model.Extension;
import io.quarkus.registry.app.model.ExtensionRelease;
import io.quarkus.registry.app.repository.CategoryRepository;
import io.quarkus.registry.app.repository.ExtensionReleaseRepository;
import io.quarkus.registry.app.repository.ExtensionRepository;
import io.restassured.http.ContentType;

/**
 * Covers the cross-cutting behaviour that the ported functional tests do not reach: the security
 * response headers, CORS, the actuator health groups the OpenShift probes rely on, the Maven
 * response cache (ETag / 304), the extension compatibility endpoints and the request dump endpoint.
 * <p>
 * The original project configured most of these declaratively in {@code application.properties}
 * (quarkus.http.header.*, quarkus.http.cors.*, SmallRye Health), so nothing exercised them. In the
 * migrated application they are real code — filters, a security chain, health groups — and a
 * regression in any of them is invisible to every other test in this suite.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
// Spring Boot disables metrics export registries in tests; without this only a SimpleMeterRegistry
// exists and the Prometheus scrape endpoint is not registered at all.
@AutoConfigureObservability
class CrossCuttingBehaviourTest extends BaseTest {

    @Autowired
    ExtensionRepository extensionRepository;

    @Autowired
    ExtensionReleaseRepository extensionReleaseRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    jakarta.persistence.EntityManagerFactory entityManagerFactory;

    private static final String MAVEN_DESCRIPTOR_JSON = "/maven/io/quarkus/registry/quarkus-registry-descriptor/1.0-SNAPSHOT/quarkus-registry-descriptor-1.0-SNAPSHOT.json";

    /**
     * The @Cacheable entities (Category, Platform) were cached by Hibernate out of the box under
     * Quarkus. Spring Boot ships no JCache provider, so the annotations are inert unless one is
     * configured - and an inert cache is invisible to every functional test. This asserts the
     * second-level cache is genuinely serving reads.
     */
    @Test
    void should_serve_cacheable_entities_from_the_hibernate_second_level_cache() {
        var sessionFactory = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class);
        sessionFactory.getCache().evictAllRegions();
        var statistics = sessionFactory.getStatistics();
        statistics.clear();

        // categories are seeded by the V2 Flyway migration
        var category = inTransactionGet(() -> categoryRepository.findAll().get(0));
        // first read by id populates the cache, subsequent reads must hit it
        inTransaction(() -> categoryRepository.findById(category.id).orElseThrow());
        inTransaction(() -> categoryRepository.findById(category.id).orElseThrow());

        org.assertj.core.api.Assertions.assertThat(statistics.isStatisticsEnabled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(statistics.getSecondLevelCachePutCount())
                .as("entities must be written into the second level cache").isGreaterThan(0L);
        org.assertj.core.api.Assertions.assertThat(statistics.getSecondLevelCacheHitCount())
                .as("subsequent reads must be served from the second level cache").isGreaterThan(0L);
    }

    private <T> T inTransactionGet(java.util.function.Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    @Test
    void should_send_the_security_response_headers() {
        given()
                .when().get("/client/platforms")
                .then()
                .header("X-Content-Type-Options", is("nosniff"))
                .header("X-Frame-Options", is("deny"))
                .header("Strict-Transport-Security", is("max-age=31536000; includeSubDomains"))
                .header("Content-Security-Policy", containsString("frame-ancestors 'none'"));
    }

    @Test
    void should_only_allow_the_same_origin() {
        // quarkus.http.cors.enabled=true with no origin configured: the server's own origin only
        String own = "http://localhost:" + io.restassured.RestAssured.port;
        given()
                .header("Origin", own)
                .header("Access-Control-Request-Method", "GET")
                .when().options("/client/platforms")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", is(own))
                .header("Access-Control-Allow-Credentials", is("false"))
                .header("Access-Control-Allow-Methods", is("GET"));
        given()
                .header("Origin", "https://example.com")
                .when().get("/client/platforms")
                .then()
                .statusCode(403);
    }

    @Test
    void should_expose_the_health_endpoints_used_by_the_openshift_probes() {
        // openshift/template.yaml probes these two exact paths, in the MicroProfile Health format
        given().when().get("/q/health/live")
                .then().statusCode(200)
                .body("status", is("UP"))
                .body("checks.name", org.hamcrest.Matchers.contains("Maven cache health"));

        given().when().get("/q/health/ready")
                .then().statusCode(200)
                .body("status", is("UP"))
                .body("checks.name", org.hamcrest.Matchers.contains("Database connections health check"))
                .body("checks[0].data.'<default>'", is("UP"));

        given().when().get("/q/health")
                .then().statusCode(200).body("status", is("UP"))
                .body("checks.name", org.hamcrest.Matchers.contains("Maven cache health",
                        "Database connections health check"));
    }

    @Test
    void should_expose_prometheus_metrics_with_the_operation_tag() {
        given().when().get("/client/platforms").then().statusCode(204);
        given().when().get("/q/metrics")
                .then()
                .statusCode(200)
                .body(containsString("http_server_requests_seconds_count"))
                .body(containsString("op=\"r\""));
    }

    @Test
    void should_not_meter_the_ignored_uris() {
        given().when().get("/index.html").then().statusCode(200);
        given().when().get("/q/metrics")
                .then()
                .statusCode(200)
                .body(org.hamcrest.CoreMatchers.not(containsString("uri=\"/index.html\"")));
    }

    @Test
    void should_serve_the_maven_cache_with_an_etag_and_answer_conditional_requests() {
        String etag = given()
                .when().get(MAVEN_DESCRIPTOR_JSON)
                .then()
                .statusCode(200)
                .header("etag", notNullValue())
                .header("cache-control", is("no-cache"))
                .header("last-modified", notNullValue())
                .extract().header("etag");

        // a second, unconditional request is served from the cache and marked as such
        given()
                .when().get(MAVEN_DESCRIPTOR_JSON)
                .then()
                .statusCode(200)
                .header("Via", is("registry.quarkus.io maven-cache"));

        // a conditional request with the same ETag is answered with 304 and no body
        given()
                .header("If-None-Match", etag)
                .when().get(MAVEN_DESCRIPTOR_JSON)
                .then()
                .statusCode(304);
    }

    @Test
    void should_create_and_delete_an_extension_compatibility() {
        inTransaction(() -> {
            Extension extension = new Extension();
            extension.name = "Compat";
            extension.description = "Compatibility fixture";
            extension.groupId = "compat.group";
            extension.artifactId = "compat-extension";
            extensionRepository.saveAndFlush(extension);

            ExtensionRelease extensionRelease = new ExtensionRelease();
            extensionRelease.extension = extension;
            extensionRelease.version = "1.0.0";
            extensionRelease.quarkusCoreVersion = "2.0.0.Final";
            extensionReleaseRepository.saveAndFlush(extensionRelease);
        });

        given()
                .header("TOKEN", "test")
                .contentType(ContentType.URLENC)
                .formParam("groupId", "compat.group")
                .formParam("artifactId", "compat-extension")
                .formParam("version", "1.0.0")
                .formParam("quarkusCore", "3.1.0.Final")
                .when().post("/admin/v1/extension/compat")
                .then().statusCode(202);

        // the extension is now reported as compatible with a core version outside its own range
        given()
                .when().get("/client/non-platform-extensions?v=3.1.0.Final")
                .then()
                .statusCode(200)
                .body("extensions.artifact", org.hamcrest.CoreMatchers.hasItem("compat.group:compat-extension::jar:1.0.0"));

        given()
                .header("TOKEN", "test")
                .contentType(ContentType.URLENC)
                .formParam("groupId", "compat.group")
                .formParam("artifactId", "compat-extension")
                .formParam("version", "1.0.0")
                .formParam("quarkusCore", "3.1.0.Final")
                .when().delete("/admin/v1/extension/compat")
                .then().statusCode(202);

        // and is gone again once the compatibility record is deleted
        given()
                .when().get("/client/non-platform-extensions?v=3.1.0.Final")
                .then()
                .statusCode(200)
                .body("extensions", is(org.hamcrest.CoreMatchers.nullValue()));
    }

    @Test
    void should_reject_a_compatibility_for_an_unknown_extension_release() {
        given()
                .header("TOKEN", "test")
                .contentType(ContentType.URLENC)
                .formParam("groupId", "does.not")
                .formParam("artifactId", "exist")
                .formParam("version", "9.9.9")
                .formParam("quarkusCore", "3.1.0.Final")
                .when().post("/admin/v1/extension/compat")
                .then().statusCode(404);
    }

    /**
     * The header NAMES are this endpoint's output, so their spelling is its contract.
     *
     * <p>This assertion previously required the lower-cased {@code x-probe}, which is how Tomcat
     * hands header names to a servlet - so it pinned the migration's own behaviour rather than the
     * original's. JAX-RS supplied them through a case-insensitive, sorted map and the original
     * printed {@code X-Probe}, alphabetically among the rest. A differential against the running
     * baseline is what surfaced the difference; this test had passed throughout.
     */
    @Test
    void should_dump_the_request_headers() {
        given()
                .header("X-Probe", "hello")
                .when().get("/client/dump-request")
                .then()
                .statusCode(200)
                .header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.TEXT_PLAIN_VALUE))
                .body(containsString("Request Headers:"))
                .body(containsString("X-Probe: [hello]"));
    }

    /** The names are canonicalised AND sorted, both of which the original did. */
    @Test
    void should_dump_the_request_headers_in_canonical_case_and_sorted_order() {
        String body = given()
                .header("X-Probe", "hello")
                .when().get("/client/dump-request")
                .then().statusCode(200)
                .extract().body().asString();

        int accept = body.indexOf("\nAccept:");
        int host = body.indexOf("\nHost:");
        int probe = body.indexOf("\nX-Probe:");
        org.junit.jupiter.api.Assertions.assertTrue(accept >= 0, "Accept absent or lower-cased: " + body);
        org.junit.jupiter.api.Assertions.assertTrue(host > accept, "Host must follow Accept: " + body);
        org.junit.jupiter.api.Assertions.assertTrue(probe > host, "X-Probe must sort last: " + body);
    }

    /**
     * {@code violations[].field} is the published shape of the 400, and it names the METHOD and the
     * parameter, not the parameter alone.
     *
     * <p>JAX-RS reported the constraint's propertyPath, so the original answered
     * {@code resolveNonPlatformExtensionsCatalog.quarkusVersion}. Spring's
     * {@code ParameterValidationResult} carries only {@code quarkusVersion}, and the port answered
     * that - a client keying off this field reads a different value. Nothing in the suite asserted
     * it: every existing assertion looks at {@code violations.message}, which is why a mutation
     * that strips the method prefix went uncaught.
     */
    @Test
    void should_name_the_method_and_the_parameter_in_a_violation_field() {
        given()
                .when().get("/client/non-platform-extensions")
                .then()
                .statusCode(400)
                .body("violations[0].field",
                        equalTo("resolveNonPlatformExtensionsCatalog.quarkusVersion"));
    }

    /** Whatever the violation, the field must be named - never null. */
    @Test
    void should_always_name_a_field_on_a_validation_failure() {
        given()
                .when().get("/client/non-platform-extensions")
                .then()
                .statusCode(400)
                .body("violations[0].field", notNullValue())
                .body("violations[0].field", containsString("quarkusVersion"));
    }

    @Test
    void should_reject_admin_writes_without_a_token_but_allow_reads() {
        given()
                .contentType(ContentType.URLENC)
                .formParam("name", "nope")
                .when().patch("/admin/v1/extension/foo.bar/foo-extension")
                .then().statusCode(403);

        given().when().get("/client/platforms").then().statusCode(204);
    }
}
