package io.quarkus.registry.app.web;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * RESTEasy Reactive and the Vert.x static handler declared {@code charset=UTF-8} on the JSON, YAML, text
 * and HTML they produced ({@code application/json;charset=UTF-8}); Spring writes JSON without a charset
 * and serves static files without one. The Maven artifacts are the exception: their resource set an
 * explicit type without a charset ({@code application/json}, {@code application/xml},
 * {@code text/plain}), which is kept as it is.
 */
public class ResteasyCharsetFilter extends OncePerRequestFilter {

    private static final Set<String> TEXTUAL = Set.of("application/json", "application/yaml", "text/plain", "text/html");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean mavenArtifact = path.startsWith("/maven/") && !path.startsWith("/maven/.meta/prefixes.txt");
        filterChain.doFilter(request, mavenArtifact ? response : new CharsetResponse(response));
    }

    private static final class CharsetResponse extends HttpServletResponseWrapper {

        CharsetResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setContentType(String type) {
            super.setContentType(withCharset(type));
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, "Content-Type".equalsIgnoreCase(name) ? withCharset(value) : value);
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, "Content-Type".equalsIgnoreCase(name) ? withCharset(value) : value);
        }

        private static String withCharset(String type) {
            if (type == null) {
                return null;
            }
            try {
                MediaType mediaType = MediaType.parseMediaType(type);
                if (mediaType.getCharset() == null && TEXTUAL.contains(mediaType.getType() + "/" + mediaType.getSubtype())) {
                    return mediaType.getType() + "/" + mediaType.getSubtype() + ";charset=UTF-8";
                }
            } catch (IllegalArgumentException e) {
                return type;
            }
            return type;
        }
    }
}
