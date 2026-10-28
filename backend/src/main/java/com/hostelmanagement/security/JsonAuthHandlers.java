package com.hostelmanagement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostelmanagement.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/** Returns JSON (instead of HTML/redirects) for 401 and 403 responses produced by the security filter chain. */
@Component
@RequiredArgsConstructor
public class JsonAuthHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(response, request, 401, "UNAUTHORIZED", "Your session has expired. Please sign in again.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, request, 403, "FORBIDDEN", "You do not have permission to perform this action");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, int status, String code, String msg)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ApiError(Instant.now(), status, code, msg, null, request.getRequestURI()));
    }
}
