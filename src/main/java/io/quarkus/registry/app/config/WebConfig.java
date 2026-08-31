package io.quarkus.registry.app.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Registers the YAML message converter that replaces the JAX-RS {@code YAMLProvider}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final YAMLMapper yamlMapper = JacksonConfig.registryYamlMapper();

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.strategies(List.of(new LenientContentNegotiationStrategy()));
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter yamlConverter = new MappingJackson2HttpMessageConverter(yamlMapper);
        yamlConverter.setSupportedMediaTypes(List.of(YAMLMediaTypes.APPLICATION_JACKSON_YAML_TYPE,
                YAMLMediaTypes.TEXT_JACKSON_YAML_TYPE));
        converters.add(yamlConverter);
    }
}
