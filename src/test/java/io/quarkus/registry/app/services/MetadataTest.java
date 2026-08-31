package io.quarkus.registry.app.services;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

import java.io.InputStream;
import java.util.List;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import io.quarkus.registry.app.BaseTest;
import io.quarkus.registry.app.maven.HashUtil;
import io.quarkus.registry.app.maven.MavenConfig;
import io.quarkus.registry.app.model.Platform;
import io.quarkus.registry.app.model.PlatformRelease;
import io.quarkus.registry.app.model.PlatformStream;
import io.quarkus.registry.app.repository.PlatformReleaseRepository;
import io.quarkus.registry.app.repository.PlatformRepository;
import io.quarkus.registry.app.repository.PlatformStreamRepository;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class MetadataTest extends BaseTest {

    @Autowired
    MavenConfig mavenConfig;

    @Autowired
    PlatformRepository platformRepository;

    @Autowired
    PlatformStreamRepository platformStreamRepository;

    @Autowired
    PlatformReleaseRepository platformReleaseRepository;

    @BeforeEach
    void setUp() {
        inTransaction(() -> {
        Platform platform = platformRepository.findByPlatformKey("io.quarkus.platform").orElseThrow();
        PlatformStream stream20 = new PlatformStream();
        stream20.platform = platform;
        stream20.streamKey = "2.0";
        platformStreamRepository.saveAndFlush(stream20);

        PlatformRelease release201 = new PlatformRelease();
        release201.platformStream = stream20;
        release201.version = "2.0.1.Final";
        release201.quarkusCoreVersion = "2.0.1.Final";
        release201.bom = "io.quarkus.platform:quarkus-bom::pom:2.0.1.Final";
        platformReleaseRepository.saveAndFlush(release201);

        PlatformRelease release202 = new PlatformRelease();
        release202.platformStream = stream20;
        release202.version = "2.0.2.Final";
        release202.quarkusCoreVersion = "2.0.2.Final";
        release202.bom = "io.quarkus.platform:quarkus-bom::pom:2.0.2.Final";
        platformReleaseRepository.saveAndFlush(release202);

        PlatformStream stream21 = new PlatformStream();
        stream21.platform = platform;
        stream21.streamKey = "2.1";
        platformStreamRepository.saveAndFlush(stream21);

        PlatformRelease release210CR1 = new PlatformRelease();
        release210CR1.platformStream = stream21;
        release210CR1.version = "2.1.0.Final";
        release210CR1.quarkusCoreVersion = "2.1.0.Final";
        release210CR1.bom = "io.quarkus.platform:quarkus-bom::pom:2.1.0.CR1";
        platformReleaseRepository.saveAndFlush(release210CR1);
        });
    }

    @Test
    public void should_contain_version() throws Exception {
        given()
                .get("/maven/"
                        + mavenConfig.getRegistryGroupId().replace('.', '/')
                        + "/quarkus-platforms/1.0-SNAPSHOT/maven-metadata.xml")
                .then()
                .statusCode(200)
                .header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_XML_VALUE))
                .body("metadata.version", is("1.0-SNAPSHOT"));
    }

    @Test
    public void should_have_classifiers() throws Exception {
        InputStream is = given()
                .get("/maven/" +
                        mavenConfig.getRegistryGroupId().replace('.', '/')
                        + "/quarkus-platforms/1.0-SNAPSHOT/maven-metadata.xml")
                .then()
                .statusCode(200)
                .header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_XML_VALUE))
                .extract().asInputStream();
        Metadata metadata = new MetadataXpp3Reader().read(is);
        List<SnapshotVersion> snapshotVersions = metadata.getVersioning().getSnapshotVersions();
        assertThat(snapshotVersions).hasSize(6);
        assertThat(snapshotVersions)
                .extracting("classifier")
                .contains("2.0.2.Final", "2.0.1.Final", "2.1.0.Final", "all");
    }

    @Test
    public void should_match_checksum() throws Exception {
        String metadata = given()
                .get("/maven/"
                        + mavenConfig.getRegistryGroupId().replace('.', '/')
                        + "/quarkus-platforms/1.0-SNAPSHOT/maven-metadata.xml")
                .then()
                .statusCode(200)
                .header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_XML_VALUE))
                .extract().body().asString();
        String expectedSha1 = HashUtil.sha1(metadata);
        given()
                .get("/maven/"
                        + mavenConfig.getRegistryGroupId().replace('.', '/')
                        + "/quarkus-platforms/1.0-SNAPSHOT/maven-metadata.xml.sha1")
                .then()
                .statusCode(200)
                .header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.TEXT_PLAIN_VALUE))
                .body(equalTo(expectedSha1));
    }
}
