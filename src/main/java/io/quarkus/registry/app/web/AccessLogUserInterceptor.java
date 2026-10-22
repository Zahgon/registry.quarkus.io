package io.quarkus.registry.app.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import io.quarkus.registry.app.admin.AdminApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The access log's user field. Quarkus logged {@code Admin} for a request that reached a
 * {@code @RolesAllowed("admin")} operation as the token-authenticated admin, and {@code -} for every
 * other request. The Tomcat access log valve prints this request attribute in that field.
 */
public class AccessLogUserInterceptor implements HandlerInterceptor {

    public static final String USER_ATTRIBUTE = "registry.accessLogUser";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && method.getBeanType() == AdminApi.class) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                request.setAttribute(USER_ATTRIBUTE, authentication.getName());
            }
        }
        return true;
    }
}
