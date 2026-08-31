package io.quarkus.registry.app.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkus.registry.app.jackson.ExtensionCatalogMixin;
import io.quarkus.registry.app.jackson.ExtensionMixin;
import io.quarkus.registry.catalog.CatalogMapperHelper;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;

/**
 * Replaces the Quarkus {@code ObjectMapperCustomizer} SPI: the registry catalog mapper
 * configuration and mixins are applied to the Spring managed {@code ObjectMapper} and to the
 * dedicated YAML mapper used by the {@code application/yaml} message converter.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer registryObjectMapperCustomizer() {
        return builder -> builder.postConfigurer(objectMapper -> {
            CatalogMapperHelper.initMapper(objectMapper);
            objectMapper.addMixIn(ExtensionCatalog.class, ExtensionCatalogMixin.class);
            objectMapper.addMixIn(Extension.class, ExtensionMixin.class);
        });
    }

    /**
     * Deliberately <em>not</em> a bean: {@link YAMLMapper} extends {@code ObjectMapper}, so exposing
     * it would satisfy Spring Boot's {@code @ConditionalOnMissingBean(ObjectMapper.class)} and
     * suppress the auto-configured JSON mapper, making every JSON response come out as YAML.
     */
    public static YAMLMapper registryYamlMapper() {
        YAMLMapper yamlMapper = new YAMLMapper();
        CatalogMapperHelper.initMapper(yamlMapper);
        yamlMapper.addMixIn(ExtensionCatalog.class, ExtensionCatalogMixin.class);
        yamlMapper.addMixIn(Extension.class, ExtensionMixin.class);
        return yamlMapper;
    }
}
