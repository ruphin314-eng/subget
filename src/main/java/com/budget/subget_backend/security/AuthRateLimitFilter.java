package com.budget.subget_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Anti brute-force sur /auth/login et /auth/register.
 * Clé = IP + route, pour ne pas pénaliser un client à cause du trafic d'un
 * autre client sur une route différente.
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean limited = "/auth/login".equals(path) || "/auth/register".equals(path);

        if (limited) {
            String key = clientIp(request) + ":" + path;
            if (!rateLimiter.tryConsume(key, MAX_REQUESTS, WINDOW)) {
                response.setStatus(429); // Too Many Requests
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Trop de tentatives, réessayez plus tard.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    // X-Forwarded-For à privilégier derrière un reverse proxy (ex: Render) ;
    // sinon repli sur l'IP de connexion directe.
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
