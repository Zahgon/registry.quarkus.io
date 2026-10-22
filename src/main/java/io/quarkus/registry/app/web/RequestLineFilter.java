package io.quarkus.registry.app.web;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.util.UriUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The request-line handling Vert.x applied before the application saw a request, ahead of every other
 * filter:
 *
 * <ul>
 * <li>a request line longer than 4096 bytes answers 414, request headers over 20480 bytes in all 431;</li>
 * <li>a declared body longer than 10 MiB answers 413, as does a form field value longer than 2048 bytes;</li>
 * <li>a query string that is not valid percent-encoding answers 400;</li>
 * <li>the path is normalised: path parameters ({@code ;x=1}) are dropped, empty and {@code .} segments
 * removed, and {@code ..} resolved, so {@code /client//platforms} and {@code /client/x/../platforms}
 * are {@code /client/platforms}.</li>
 * </ul>
 * Each refusal carries no body.
 */
public class RequestLineFilter extends OncePerRequestFilter {

    static final int MAX_INITIAL_LINE_LENGTH = 4096;

    static final int MAX_HEADER_SIZE = 20480;

    static final long MAX_BODY_SIZE = 10L * 1024 * 1024;

    static final int MAX_FORM_ATTRIBUTE_SIZE = 2048;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String query = request.getQueryString();
        String target = request.getRequestURI() + (query == null ? "" : "?" + query);
        int lineLength = request.getMethod().length() + 1 + target.length() + 1 + request.getProtocol().length();
        if (lineLength > MAX_INITIAL_LINE_LENGTH) {
            refuse(response, HttpServletResponse.SC_REQUEST_URI_TOO_LONG);
            return;
        }
        if (headerSize(request) > MAX_HEADER_SIZE) {
            refuse(response, 431);
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_SIZE) {
            refuse(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        if (query != null && !decodable(query)) {
            refuse(response, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String path = normalize(request.getRequestURI());
        HttpServletRequest normalized = path.equals(request.getRequestURI()) ? request
                : new NormalizedPathRequest(request, path);
        if (isForm(request)) {
            FormRequest form = new FormRequest(normalized);
            if (form.hasOversizedAttribute()) {
                refuse(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                return;
            }
            normalized = form;
        }
        filterChain.doFilter(normalized, response);
    }

    private static long headerSize(HttpServletRequest request) {
        long size = 0;
        for (String name : java.util.Collections.list(request.getHeaderNames())) {
            for (String value : java.util.Collections.list(request.getHeaders(name))) {
                size += name.length() + 2 + value.length() + 2;
            }
        }
        return size;
    }

    private static void refuse(HttpServletResponse response, int status) {
        response.setStatus(status);
        response.setHeader("Connection", "close");
        response.setContentLength(0);
    }

    private static boolean decodable(String query) {
        try {
            URLDecoder.decode(query, StandardCharsets.UTF_8);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isForm(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_FORM_URLENCODED.includes(MediaType.parseMediaType(contentType));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String normalize(String uri) {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : uri.split("/", -1)) {
            int semicolon = segment.indexOf(';');
            String value = semicolon >= 0 ? segment.substring(0, semicolon) : segment;
            if (value.isEmpty() || value.equals(".")) {
                continue;
            }
            if (value.equals("..")) {
                segments.pollLast();
                continue;
            }
            segments.addLast(value);
        }
        StringBuilder path = new StringBuilder();
        for (String segment : segments) {
            path.append('/').append(segment);
        }
        if (path.isEmpty()) {
            return "/";
        }
        String last = uri.substring(uri.lastIndexOf('/') + 1);
        int semicolon = last.indexOf(';');
        last = semicolon >= 0 ? last.substring(0, semicolon) : last;
        if (last.isEmpty() || last.equals(".") || last.equals("..")) {
            path.append('/');
        }
        return path.toString();
    }

    /** The request with its path normalised; the context path is empty, so the servlet path is the path. */
    private static final class NormalizedPathRequest extends HttpServletRequestWrapper {

        private final String path;

        private final String servletPath;

        NormalizedPathRequest(HttpServletRequest request, String path) {
            super(request);
            this.path = path;
            // The servlet path is decoded; an encoded slash stays encoded so it never becomes a separator
            String lower = path.toLowerCase(java.util.Locale.ROOT);
            this.servletPath = lower.contains("%2f") ? path : UriUtils.decode(path, StandardCharsets.UTF_8);
        }

        /** Only the original request is renormalised; a forward or include carries its own path. */
        private boolean original() {
            return getDispatcherType() == jakarta.servlet.DispatcherType.REQUEST;
        }

        @Override
        public String getRequestURI() {
            return original() ? getContextPath() + path : super.getRequestURI();
        }

        @Override
        public String getServletPath() {
            return original() ? servletPath : super.getServletPath();
        }

        @Override
        public String getPathInfo() {
            return original() ? null : super.getPathInfo();
        }

        @Override
        public StringBuffer getRequestURL() {
            if (!original()) {
                return super.getRequestURL();
            }
            StringBuffer url = new StringBuffer();
            url.append(getScheme()).append("://").append(getServerName());
            int port = getServerPort();
            if (port > 0 && !(("http".equals(getScheme()) && port == 80) || ("https".equals(getScheme()) && port == 443))) {
                url.append(':').append(port);
            }
            return url.append(getRequestURI());
        }
    }

    /**
     * A form-encoded request whose body is read here, once, to check every field against Vert.x's
     * 2048-byte form attribute limit, and then replayed: as the body stream for Spring's form handling
     * of PUT, PATCH and DELETE, and as the parameters (query first, then body) for every method.
     */
    private static final class FormRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private final Map<String, String[]> parameters;

        FormRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
            Map<String, java.util.List<String>> merged = new java.util.LinkedHashMap<>();
            parse(request.getQueryString(), merged);
            String charset = request.getCharacterEncoding() == null ? "UTF-8" : request.getCharacterEncoding();
            parse(new String(body, java.nio.charset.Charset.forName(charset)), merged);
            Map<String, String[]> values = new java.util.LinkedHashMap<>();
            merged.forEach((name, list) -> values.put(name, list.toArray(String[]::new)));
            this.parameters = java.util.Collections.unmodifiableMap(values);
        }

        private static void parse(String encoded, Map<String, java.util.List<String>> into) {
            if (encoded == null || encoded.isEmpty()) {
                return;
            }
            for (String pair : encoded.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                into.computeIfAbsent(name, n -> new java.util.ArrayList<>()).add(value);
            }
        }

        boolean hasOversizedAttribute() {
            return parameters.values().stream().flatMap(java.util.Arrays::stream)
                    .anyMatch(value -> value.getBytes(StandardCharsets.UTF_8).length > MAX_FORM_ATTRIBUTE_SIZE);
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(body);
            return new jakarta.servlet.ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() throws IOException {
            String charset = getCharacterEncoding() == null ? "UTF-8" : getCharacterEncoding();
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), charset));
        }

        @Override
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return parameters;
        }

        @Override
        public java.util.Enumeration<String> getParameterNames() {
            return java.util.Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return parameters.get(name);
        }
    }
}
