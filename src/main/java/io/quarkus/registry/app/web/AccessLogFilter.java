package io.quarkus.registry.app.web;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * {@code quarkus.http.access-log.enabled=true} with {@code exclude-pattern=/q/.*}: one line per request in
 * the common log format, {@code <client> - <user> [<time>] "<request line>" <status> <body bytes or ->},
 * written through the application log so it shares standard output with every other line. The user is
 * {@code Admin} for a request that reached an admin operation as the token-authenticated admin
 * ({@link AccessLogUserInterceptor}) and {@code -} otherwise.
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("io.quarkus.registry.access-log");

    private static final DateTimeFormatter CLF_TIME = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    private final Pattern excluded;

    public AccessLogFilter(String excludePattern) {
        this.excluded = excludePattern == null || excludePattern.isBlank() ? null : Pattern.compile(excludePattern);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (excluded != null && excluded.matcher(request.getRequestURI()).matches()) {
            filterChain.doFilter(request, response);
            return;
        }
        ZonedDateTime started = ZonedDateTime.now(ZoneOffset.UTC);
        CountingResponse counting = new CountingResponse(response);
        try {
            filterChain.doFilter(request, counting);
        } finally {
            String query = request.getQueryString();
            Object user = request.getAttribute(AccessLogUserInterceptor.USER_ATTRIBUTE);
            ACCESS_LOG.info("{} - {} [{}] \"{} {}{} {}\" {} {}", request.getRemoteAddr(), user == null ? "-" : user,
                    CLF_TIME.format(started), request.getMethod(), request.getRequestURI(), query == null ? "" : "?" + query,
                    request.getProtocol(), counting.getStatus(), counting.bytes == 0 ? "-" : String.valueOf(counting.bytes));
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    private static final class CountingResponse extends HttpServletResponseWrapper {

        private long bytes;

        private ServletOutputStream stream;

        private java.io.PrintWriter writer;

        CountingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (stream == null) {
                ServletOutputStream delegate = super.getOutputStream();
                stream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return delegate.isReady();
                    }

                    @Override
                    public void setWriteListener(WriteListener listener) {
                        delegate.setWriteListener(listener);
                    }

                    @Override
                    public void write(int b) throws IOException {
                        delegate.write(b);
                        bytes++;
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        delegate.write(b, off, len);
                        bytes += len;
                    }

                    @Override
                    public void flush() throws IOException {
                        delegate.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        delegate.close();
                    }
                };
            }
            return stream;
        }

        @Override
        public java.io.PrintWriter getWriter() throws IOException {
            if (writer == null) {
                String charset = getCharacterEncoding() == null ? "ISO-8859-1" : getCharacterEncoding();
                writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(getOutputStream(), charset), false);
            }
            return writer;
        }

        @Override
        public void flushBuffer() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            super.flushBuffer();
        }
    }
}
