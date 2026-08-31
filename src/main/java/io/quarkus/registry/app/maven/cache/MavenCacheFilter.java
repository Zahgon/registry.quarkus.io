package io.quarkus.registry.app.maven.cache;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Serve the response from cache if available
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MavenCacheFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MavenCacheFilter.class);

    private final MavenCache mavenCache;

    @Value("${maven.cache.path:/maven}")
    String pathToCache;

    @Value("${maven.cache.header.cache-control:no-cache}") // Check for etag everytime
    String headerCacheControl;

    @Value("${maven.cache.enabled:true}")
    boolean enabled;

    public MavenCacheFilter(MavenCache mavenCache) {
        this.mavenCache = mavenCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isMavenGetRequest(request) || !enabled) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = getPath(request);
        MavenResponse mavenResponse = mavenCache.getCache().getIfPresent(path);
        if (mavenResponse != null) {
            // Check if we can response with Not Modified
            if (notModified(request, mavenResponse)) {
                writeNotModifiedResponse(response, mavenResponse);
            } else {
                log.debug("Serving [{}] from cache", path);
                writeResponse(response, mavenResponse);
            }
            return;
        }
        // Else fall through to the actual endpoint
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, responseWrapper);
            if (responseWrapper.getStatus() == 200 && !isCachedResponse(responseWrapper)) {
                MavenResponse toCache = toMavenResponse(responseWrapper);
                log.debug("Caching [{}]", path);
                mavenCache.getCache().put(path, toCache);
            }
        } finally {
            responseWrapper.copyBodyToResponse();
        }
    }

    private boolean notModified(HttpServletRequest request, MavenResponse cachedResponse) {
        // Here we first check the request header for If-None-Match (etag) or If-Modified-Since (last-modified)

        // First Check Etag
        String givenEtag = request.getHeader(IF_NONE_MATCH);
        String cachedEtag = cachedResponse.getHeaders().get(ETAG);
        if (givenEtag != null && givenEtag.equals(cachedEtag)) {
            return true;
        }

        // Secondly Check Last Modified
        String givenLastModified = request.getHeader(IF_MODIFIED_SINCE);
        String cachedLastModified = cachedResponse.getHeaders().get(LAST_MODIFIED);
        return givenLastModified != null && givenLastModified.equals(cachedLastModified);
    }

    private MavenResponse toMavenResponse(ContentCachingResponseWrapper responseWrapper) {
        byte[] content = responseWrapper.getContentAsByteArray();
        MavenResponse mavenResponse = new MavenResponse(responseWrapper.getStatus(), content);

        // We add some cache related headers here for when using via a CDN etc.
        // see https://www.keycdn.com/blog/http-cache-headers#no-cache-and-no-store

        // Cache control
        responseWrapper.addHeader(CACHE_CONTROL, headerCacheControl);

        // Etag (to be compared with If-None-Match)
        // We try and set this based on the actual content. This means that if the content did not change (even if we recreate the cache)
        // the etag will be the same.
        if (responseWrapper.getHeader(ETAG) == null) {
            // etag - We create a hash from the Response content, so even if the cache clears, if the content is the same, the etag will also be
            responseWrapper.addHeader(ETAG, UUID.nameUUIDFromBytes(content).toString());
        }

        // Last-Modified (to be compared with If-Modified-Since)
        // For now we set the date when we cached this, but a better way would be to get a date from when this was persisted (changed)
        LocalDateTime cachedAt = LocalDateTime.now(ZoneOffset.UTC);
        responseWrapper.addHeader(LAST_MODIFIED, cachedAt.format(lastModifiedDateFormatter));

        // Now we should have all the headers we want to cache
        for (String key : responseWrapper.getHeaderNames()) {
            mavenResponse.addHeader(key, responseWrapper.getHeader(key));
        }

        return mavenResponse;
    }

    private void writeNotModifiedResponse(HttpServletResponse response, MavenResponse mavenResponse) {
        response.setStatus(NOT_MODIFIED_STATUS);
        response.setHeader(VIA, VIA_CACHE);
        for (Map.Entry<String, String> header : mavenResponse.getHeaders().entrySet()) {
            response.setHeader(header.getKey(), header.getValue());
        }
    }

    private void writeResponse(HttpServletResponse response, MavenResponse mavenResponse) throws IOException {
        response.setStatus(mavenResponse.getStatus());
        response.setHeader(VIA, VIA_CACHE);
        for (Map.Entry<String, String> header : mavenResponse.getHeaders().entrySet()) {
            response.setHeader(header.getKey(), header.getValue());
        }
        byte[] body = mavenResponse.getResponse();
        if (body != null && body.length > 0) {
            response.getOutputStream().write(body);
        }
    }

    private boolean isMavenGetRequest(HttpServletRequest request) {
        return getPath(request).startsWith(pathToCache) && GET.equalsIgnoreCase(request.getMethod());
    }

    private boolean isCachedResponse(HttpServletResponse response) {
        return VIA_CACHE.equals(response.getHeader(VIA));
    }

    private static String getPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private static final String GET = "GET";
    private static final String VIA = "Via";
    private static final String VIA_CACHE = "registry.quarkus.io maven-cache";
    private static final String ETAG = "etag";
    private static final String CACHE_CONTROL = "cache-control";
    private static final String LAST_MODIFIED = "last-modified";
    private static final String IF_NONE_MATCH = "if-none-match";
    private static final String IF_MODIFIED_SINCE = "if-modified-since";

    private static final int NOT_MODIFIED_STATUS = 304;

    private static final DateTimeFormatter lastModifiedDateFormatter = DateTimeFormatter.ofPattern(
            "EEE, dd MMM yyyy HH:mm:ss' GMT'");
}
