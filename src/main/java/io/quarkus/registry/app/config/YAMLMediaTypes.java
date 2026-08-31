package io.quarkus.registry.app.config;

import org.springframework.http.MediaType;

/**
 * The YAML media types previously provided by
 * {@code com.fasterxml.jackson.jakarta.rs.yaml.YAMLMediaTypes}.
 */
public final class YAMLMediaTypes {

    public static final String APPLICATION_JACKSON_YAML = "application/yaml";

    public static final String TEXT_JACKSON_YAML = "text/yaml";

    public static final MediaType APPLICATION_JACKSON_YAML_TYPE = MediaType
            .parseMediaType(APPLICATION_JACKSON_YAML);

    public static final MediaType TEXT_JACKSON_YAML_TYPE = MediaType.parseMediaType(TEXT_JACKSON_YAML);

    private YAMLMediaTypes() {
    }
}
