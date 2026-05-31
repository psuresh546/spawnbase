package com.spawnbase.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spawnbase.gateway.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = new ObjectMapper();
    }

    private static final List<String> PUBLIC_PATHS = List.of(
            "/actuator",
            "/api/auth"
    );

    private static final List<String> MUTATING_METHODS = List.of(
            "POST", "PATCH", "PUT", "DELETE"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // ─────────────────────────────────────────
        // CORS — single source of truth.
        // No CorsConfig bean, no properties CORS.
        // Only this filter sets CORS headers so
        // there are never duplicate header values.
        // ─────────────────────────────────────────
        response.setHeader(
                "Access-Control-Allow-Origin",
                "http://localhost:3000");
        response.setHeader(
                "Access-Control-Allow-Methods",
                "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        response.setHeader(
                "Access-Control-Allow-Headers",
                "Authorization,Content-Type,Accept,Origin," +
                        "X-Requested-With");
        response.setHeader(
                "Access-Control-Allow-Credentials",
                "true");
        response.setHeader(
                "Access-Control-Max-Age",
                "3600");

        // ─────────────────────────────────────────
        // OPTIONS preflight — respond 200 immediately.
        // flushBuffer() ensures the response is sent
        // before returning — without it the browser
        // receives an empty response (ERR_EMPTY_RESPONSE).
        // ─────────────────────────────────────────
        if ("OPTIONS".equalsIgnoreCase(method)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.flushBuffer();
            return;
        }

        // Public paths — skip JWT validation
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract token from Authorization header
        String authHeader = request.getHeader("Authorization");
        String token = extractToken(authHeader);

        if (token == null) {
            log.warn("No JWT token — path: {}", path);
            sendError(response, HttpStatus.UNAUTHORIZED,
                    "Missing Authorization header. " +
                            "Expected: Bearer <token>");
            return;
        }

        // Validate token signature and expiration
        if (!jwtUtil.isValid(token)) {
            log.warn("Invalid JWT token — path: {}", path);
            sendError(response, HttpStatus.UNAUTHORIZED,
                    "Invalid or expired JWT token");
            return;
        }

        // Extract user info from token claims
        String userId = jwtUtil.getSubject(token);
        List<String> roles = jwtUtil.getRoles(token);

        // RBAC — mutating operations require ADMIN role
        if (MUTATING_METHODS.contains(method.toUpperCase())) {
            if (!roles.contains("ADMIN")) {
                log.warn("RBAC denied — user: {} method: {}",
                        userId, method);
                sendError(response, HttpStatus.FORBIDDEN,
                        "Insufficient permissions. " +
                                "ADMIN role required for " + method);
                return;
            }
        }

        request.setAttribute("X-User-Id", userId);
        request.setAttribute("X-User-Roles",
                String.join(",", roles));

        log.info("JWT authorized — user: {} {} {}",
                userId, method, path);

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private String extractToken(String authHeader) {
        if (authHeader != null
                && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private void sendError(
            HttpServletResponse response,
            HttpStatus status,
            String message) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        );

        response.getWriter().write(
                objectMapper.writeValueAsString(body));
    }
}