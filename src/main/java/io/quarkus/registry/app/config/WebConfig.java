package io.quarkus.registry.app.config;

import java.lang.reflect.Type;
import java.util.List;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.filter.UrlHandlerFilter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkus.registry.app.web.AccessLogFilter;
import io.quarkus.registry.app.web.AccessLogUserInterceptor;
import io.quarkus.registry.app.web.RequestLineFilter;
import io.quarkus.registry.app.web.ResteasyCharsetFilter;

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
        // Spring Framework 6.2 registers its own YAML converter ahead of the one above when
        // jackson-dataformat-yaml is present. Its mapper lacks the registry mixins, so it cannot build
        // an Extension or an ExtensionCatalog from a request body, which the YAMLProvider could. This
        // reader goes first for request bodies only; responses keep the converters they had.
        converters.add(0, new RegistryYamlReader(yamlMapper));
    }

    /**
     * RESTEasy matched {@code /client/platforms/} like {@code /client/platforms}; Spring 6 no longer
     * matches a trailing slash, so the request is presented without it before anything else sees it.
     */
    @Bean
    public FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
        FilterRegistrationBean<UrlHandlerFilter> registration = new FilterRegistrationBean<>(
                UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(new AccessLogUserInterceptor());
    }

    @Bean
    public FilterRegistrationBean<ResteasyCharsetFilter> charsetFilter() {
        FilterRegistrationBean<ResteasyCharsetFilter> registration = new FilterRegistrationBean<>(new ResteasyCharsetFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return registration;
    }

    /** The access log sees every request first and logs it last, refusals included. */
    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilter(
            @org.springframework.beans.factory.annotation.Value("${registry.access-log.exclude-pattern:}") String excludePattern,
            @org.springframework.beans.factory.annotation.Value("${registry.access-log.enabled:true}") boolean enabled) {
        FilterRegistrationBean<AccessLogFilter> registration = new FilterRegistrationBean<>(new AccessLogFilter(excludePattern));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setEnabled(enabled);
        return registration;
    }

    /** Vert.x's request-line limits and path normalisation, before anything else sees the request. */
    @Bean
    public FilterRegistrationBean<RequestLineFilter> requestLineFilter() {
        FilterRegistrationBean<RequestLineFilter> registration = new FilterRegistrationBean<>(new RequestLineFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /** Tomcat rejects an encoded slash with 400 by default; Vert.x left it to routing. */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> encodedSlashPassThrough() {
        // TRACE reaches the filter chain too (Tomcat answers it 405 itself by default), so it is refused
        // like any other write without the token.
        return factory -> factory.addConnectorCustomizers(connector -> {
            connector.setEncodedSolidusHandling(EncodedSolidusHandling.PASS_THROUGH.getValue());
            connector.setAllowTrace(true);
        });
    }

    private static final class RegistryYamlReader extends MappingJackson2HttpMessageConverter {

        RegistryYamlReader(YAMLMapper mapper) {
            super(mapper);
            setSupportedMediaTypes(List.of(YAMLMediaTypes.APPLICATION_JACKSON_YAML_TYPE));
        }

        @Override
        public boolean canWrite(Class<?> clazz, MediaType mediaType) {
            return false;
        }

        @Override
        public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
            return false;
        }
    }
}
