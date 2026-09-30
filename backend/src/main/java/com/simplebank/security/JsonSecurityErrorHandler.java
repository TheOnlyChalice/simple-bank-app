package com.simplebank.security;

import com.simplebank.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Errors from the security filters (before a controller is reached), in the same JSON
 * shape as every other error:
 *   401 - no token, or the token is invalid or expired
 *   403 - logged in, but the endpoint is for bank staff only (also recorded in the audit log)
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final AuditService auditService;

    public JsonSecurityErrorHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean sentToken = header != null && header.startsWith("Bearer ");
        String message = sentToken
                ? "Your login token is invalid or has expired. Please log in again."
                : "Authentication required. Log in at POST /api/auth/login and send the token as 'Authorization: Bearer <token>'.";
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, 401, "Unauthorized", message);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        auditService.recordAccessDenied(null, null, "Endpoint is for bank staff (ADMIN) only");
        write(response, 403, "Forbidden", "This endpoint is only available to bank staff (ADMIN).");
    }

    /** The messages above never contain quotes, so they can be placed in the JSON directly. */
    private static void write(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\""
                + message + "\",\"fieldErrors\":{},\"timestamp\":\"" + LocalDateTime.now() + "\"}");
    }
}
