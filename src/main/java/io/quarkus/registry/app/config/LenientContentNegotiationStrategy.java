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

    private static final MediaType UNMATCHABLE = new MediaType("x-not-acceptable", "x-not-acceptable");

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
            // A token that is not a media type at all (no type/subtype) matches nothing, so the request
            // answers 406, as RESTEasy answered it; only its unusable parameters are forgiven.
            if (isParsable(sanitized)) {
                return MediaType.parseMediaType(sanitized);
            }
            // "*/json" is not a media type to Spring; RESTEasy read a wildcard type as any type
            return sanitized.startsWith("*/") ? MediaType.ALL : UNMATCHABLE;
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
