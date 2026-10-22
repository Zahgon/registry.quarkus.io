package io.quarkus.registry.app.openapi;

import java.util.Locale;

import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The OpenAPI document where SmallRye OpenAPI served it: {@code /q/openapi} is YAML unless the request
 * asks for JSON with {@code ?format=json} or an {@code Accept} naming {@code application/json};
 * {@code /q/openapi.yaml} and {@code /q/openapi.json} name the format in the path. springdoc builds the
 * document (its own endpoint is {@code springdoc.api-docs.path}), this controller only chooses the format.
 */
@RestController
@Hidden
public class OpenApiDocumentController {

    private static final MediaType APPLICATION_YAML = new MediaType("application", "yaml", java.nio.charset.StandardCharsets.UTF_8);

    private static final MediaType APPLICATION_JSON = new MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8);

    private final OpenApiWebMvcResource openApiResource;

    private final String apiDocsPath;

    public OpenApiDocumentController(OpenApiWebMvcResource openApiResource,
            @Value("${springdoc.api-docs.path}") String apiDocsPath) {
        this.openApiResource = openApiResource;
        this.apiDocsPath = apiDocsPath;
    }

    @GetMapping("/q/openapi")
    public ResponseEntity<byte[]> openapi(HttpServletRequest request, Locale locale,
            @RequestParam(name = "format", required = false) String format,
            @RequestHeader(name = HttpHeaders.ACCEPT, required = false) String accept) throws JsonProcessingException {
        boolean json = format != null ? "json".equalsIgnoreCase(format)
                : accept != null && MediaType.parseMediaTypes(accept).stream()
                        .anyMatch(type -> type.isCompatibleWith(MediaType.APPLICATION_JSON) && !type.isWildcardType());
        return json ? json(request, locale) : yaml(request, locale);
    }

    @GetMapping("/q/openapi.yaml")
    public ResponseEntity<byte[]> yaml(HttpServletRequest request, Locale locale) throws JsonProcessingException {
        return ResponseEntity.ok().contentType(APPLICATION_YAML)
                .body(openApiResource.openapiYaml(request, apiDocsPath, locale));
    }

    @GetMapping("/q/openapi.json")
    public ResponseEntity<byte[]> json(HttpServletRequest request, Locale locale) throws JsonProcessingException {
        return ResponseEntity.ok().contentType(APPLICATION_JSON)
                .body(openApiResource.openapiJson(request, apiDocsPath, locale));
    }
}
