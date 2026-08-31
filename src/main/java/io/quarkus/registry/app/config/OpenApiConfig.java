package io.quarkus.registry.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Replaces the MicroProfile OpenAPI {@code mp.openapi.extensions.smallrye.info.*} configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI registryOpenAPI(@Value("${registry.openapi.title}") String title,
            @Value("${registry.openapi.version}") String version,
            @Value("${registry.openapi.description}") String description,
            @Value("${registry.openapi.contact.name}") String contactName,
            @Value("${registry.openapi.contact.email}") String contactEmail,
            @Value("${registry.openapi.contact.url}") String contactUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .version(version)
                        .description(description)
                        .contact(new Contact().name(contactName).email(contactEmail).url(contactUrl)))
                .components(new Components()
                        .addSecuritySchemes("Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .name("TOKEN")
                                .in(SecurityScheme.In.HEADER)));
    }
}
