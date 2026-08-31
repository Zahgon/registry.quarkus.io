package io.quarkus.registry.app.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * RESTEasy accepts {@code Accept} headers carrying unusable parameters (for example
 * {@code application/json;charset=utf-7}) whereas Spring rejects the whole header with 406. This
 * strategy keeps the RESTEasy behaviour by dropping only the offending parameters.
 */
public class LenientContentNegotiationStrategy implements ContentNegotiationStrategy {

    @Override
    public List<MediaType> resolveMediaTypes(NativeWebRequest request) {
        String[] headerValues = request.getHeaderValues(HttpHeaders.ACCEPT);
        if (headerValues == null) {
            return MEDIA_TYPE_ALL_LIST;
        }
        List<MediaType> mediaTypes = new ArrayList<>();
        for (String headerValue : headerValues) {
            for (String token : MimeTypeUtils.tokenize(headerValue)) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    mediaTypes.add(parseLeniently(trimmed));
                }
            }
        }
        if (mediaTypes.isEmpty()) {
            return MEDIA_TYPE_ALL_LIST;
        }
        mediaTypes.sort(MediaType.SPECIFICITY_COMPARATOR);
        return mediaTypes;
    }

    private static MediaType parseLeniently(String token) {
        try {
            return MediaType.parseMediaType(token);
        } catch (InvalidMediaTypeException e) {
            String[] parts = token.split(";");
            String sanitized = parts[0].trim();
            for (int i = 1; i < parts.length; i++) {
                String candidate = sanitized + ";" + parts[i].trim();
                if (isParsable(candidate)) {
                    sanitized = candidate;
                }
            }
            return isParsable(sanitized) ? MediaType.parseMediaType(sanitized) : MediaType.ALL;
        }
    }

    private static boolean isParsable(String mediaType) {
        try {
            MediaType.parseMediaType(mediaType);
            return true;
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
