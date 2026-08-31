package io.quarkus.registry.app.services;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.quarkus.registry.app.PostgresTestContainer;
import io.restassured.RestAssured;

/**
 * Replaces the Quarkus {@code @QuarkusTestResource(restrictToAnnotatedClass = true)} configuration override with a
 * dedicated Spring application context carrying the same custom registry settings.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "quarkus.registry.groupId=foo",
        "quarkus.registry.non-platform-extensions.support=false",
        "quarkus.registry.quarkus-versions.expression=[2.1.0.Final,)",
        "quarkus.registry.quarkus-versions.exclusive-provider=true",
        "quarkus.registry.id=custom" })
@ActiveProfiles("test")
public class CustomGroupMavenResourceTest {

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registerProperties(registry);
    }

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
    }

    @Test
    void should_use_custom_descriptor_settings() {
        given()
                .get("/maven/foo/quarkus-registry-descriptor/1.0-SNAPSHOT/quarkus-registry-descriptor-1.0-SNAPSHOT.json")
                .then()
                .statusCode(200)
                .header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_JSON_VALUE))
                .body("descriptor.artifact", is("foo:quarkus-registry-descriptor::json:1.0-SNAPSHOT"),
                        "platforms.artifact", is("foo:quarkus-platforms::json:1.0-SNAPSHOT"),
                        "non-platforms-extensions.artifact", is(nullValue()),
                        "quarkus-versions.recognized-versions-expression", is("[2.1.0.Final,)"),
                        "quarkus-versions.exclusive-provider", is(true),
                        "maven.repository.id", is("custom"));
    }
}
