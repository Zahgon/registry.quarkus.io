package io.quarkus.registry.app.services;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import io.quarkus.registry.app.BaseTest;
import io.quarkus.registry.app.maven.MavenConfig;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class RepositoryMetadataResourceTest extends BaseTest {

    @Autowired
    MavenConfig mavenConfig;

    @Test
    void should_support_repository_proxied_as_nexus_repository() {
        given()
                .get("/maven/.meta/repository-metadata.xml")
                .then()
                .statusCode(200)
                .header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_XML_VALUE));

        given()
                .get("/maven/.meta/repository-metadata.xml.sha1")
                .then()
                .statusCode(200)
                .header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.TEXT_PLAIN_VALUE));

        given()
                .get("/maven/.meta/prefixes.txt")
                .then()
                .statusCode(200)
                .header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.TEXT_PLAIN_VALUE))
                .body(is("## repository-prefixes/2.0\n/" + mavenConfig.getRegistryGroupId().replace('.', '/')));
    }

}
