package io.quarkus.registry.app.maven.cache;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ServerHttpObservationFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Serves {@code GET} and {@code HEAD} under the Maven path from the cache when it can, and answers
 * conditional requests.
 *
 * <p>The validators depend on the data, never on the instance or the moment of caching, so every instance
 * on one database answers the same ones: {@code ETag} is the quoted SHA-1 of the body and
 * {@code Last-Modified} is the data generation ({@code db_state.updated_at}) to the second. An entry
 * rendered for an older generation is never served. {@code If-None-Match} ({@code *} or a list of tags,
 * compared weakly) decides when present; otherwise {@code If-Modified-Since} does. A match answers 304
 * with the validators and no body. {@code HEAD} answers the headers {@code GET} would, without the body.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MavenCacheFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MavenCacheFilter.class);

    private final MavenCache mavenCache;

    private final MavenCacheState mavenCacheState;

    @Value("${maven.cache.path:/maven}")
    String pathToCache;

    @Value("${maven.cache.header.cache-control:no-cache}") // Check for etag everytime
    String headerCacheControl;

    @Value("${maven.cache.enabled:true}")
    boolean enabled;

    public MavenCacheFilter(MavenCache mavenCache, MavenCacheState mavenCacheState) {
        this.mavenCache = mavenCache;
        this.mavenCacheState = mavenCacheState;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean head = HEAD.equalsIgnoreCase(request.getMethod());
        if (!(head || GET.equalsIgnoreCase(request.getMethod())) || !getPath(request).startsWith(pathToCache)) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = getPath(request);
        Instant generation = mavenCacheState.currentGeneration();
        MavenResponse cached = enabled ? mavenCache.getCache().getIfPresent(path) : null;
        if (cached != null && !generation.equals(cached.generation())) {
            cached = null;
        }
        MavenResponse mavenResponse = cached;
        if (mavenResponse == null) {
            // Render through the endpoint as a GET, so a HEAD gets exactly the headers of the GET
            ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
            filterChain.doFilter(head ? new GetRequest(request) : request, responseWrapper);
            if (responseWrapper.getStatus() != HttpServletResponse.SC_OK) {
                if (head) {
                    responseWrapper.resetBuffer();
                }
                responseWrapper.copyBodyToResponse();
                return;
            }
            mavenResponse = toMavenResponse(responseWrapper, generation, matchedPattern(request));
            responseWrapper.resetBuffer();
            if (enabled) {
                log.debug("Caching [{}]", path);
                mavenCache.getCache().put(path, mavenResponse);
            }
        } else {
            log.debug("Serving [{}] from cache", path);
            response.setHeader(VIA, VIA_CACHE);
            String pattern = mavenResponse.pathPattern();
            if (pattern != null) {
                ServerHttpObservationFilter.findObservationContext(request).ifPresent(context -> context.setPathPattern(pattern));
            }
        }
        boolean notModified = notModified(request, mavenResponse);
        for (Map.Entry<String, String> header : mavenResponse.headers().entrySet()) {
            if (!(notModified && header.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_TYPE))) {
                response.setHeader(header.getKey(), header.getValue());
            }
        }
        if (notModified) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentLength(mavenResponse.body().length);
        if (!head) {
            response.getOutputStream().write(mavenResponse.body());
        }
    }

    private static String matchedPattern(HttpServletRequest request) {
        return ServerHttpObservationFilter.findObservationContext(request)
                .map(context -> context.getPathPattern())
                .orElse(null);
    }

    private MavenResponse toMavenResponse(ContentCachingResponseWrapper responseWrapper, Instant generation,
            String pathPattern) {
        byte[] content = responseWrapper.getContentAsByteArray();
        Map<String, String> headers = new LinkedHashMap<>();
        for (String key : responseWrapper.getHeaderNames()) {
            if (!key.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
                headers.put(key, responseWrapper.getHeader(key));
            }
        }
        if (responseWrapper.getContentType() != null) {
            headers.put(HttpHeaders.CONTENT_TYPE, responseWrapper.getContentType());
        }
        // We add some cache related headers here for when using via a CDN etc.
        // see https://www.keycdn.com/blog/http-cache-headers#no-cache-and-no-store
        headers.put(HttpHeaders.CACHE_CONTROL, headerCacheControl);
        headers.put(HttpHeaders.ETAG, "\"" + sha1(content) + "\"");
        headers.put(HttpHeaders.LAST_MODIFIED, HTTP_DATE.format(generation.truncatedTo(ChronoUnit.SECONDS)
                .atZone(ZoneOffset.UTC)));
        return new MavenResponse(generation, content, headers, pathPattern);
    }

    private static boolean notModified(HttpServletRequest request, MavenResponse response) {
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (ifNoneMatch != null) {
            String current = opaque(response.header(HttpHeaders.ETAG));
            for (String tag : ifNoneMatch.split(",")) {
                String candidate = tag.trim();
                if (candidate.equals("*") || opaque(candidate).equals(current)) {
                    return true;
                }
            }
            return false;
        }
        String ifModifiedSince = request.getHeader(HttpHeaders.IF_MODIFIED_SINCE);
        if (ifModifiedSince == null) {
            return false;
        }
        try {
            ZonedDateTime since = ZonedDateTime.parse(ifModifiedSince.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
            ZonedDateTime lastModified = ZonedDateTime.parse(response.header(HttpHeaders.LAST_MODIFIED),
                    DateTimeFormatter.RFC_1123_DATE_TIME);
            return !lastModified.isAfter(since);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** The tag without its weakness indicator, for weak comparison. */
    private static String opaque(String tag) {
        return tag.startsWith("W/") ? tag.substring(2) : tag;
    }

    private static String sha1(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String getPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /** A HEAD request presented to the endpoint as the GET it mirrors. */
    private static final class GetRequest extends HttpServletRequestWrapper {

        GetRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getMethod() {
            return GET;
        }
    }

    private static final String GET = "GET";
    private static final String HEAD = "HEAD";
    private static final String VIA = "Via";
    private static final String VIA_CACHE = "registry.quarkus.io maven-cache";

    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME
            .withLocale(java.util.Locale.ENGLISH);
}
