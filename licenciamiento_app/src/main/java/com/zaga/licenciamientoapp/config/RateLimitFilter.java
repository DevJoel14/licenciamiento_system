package com.zaga.licenciamientoapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaga.licenciamientoapp.web.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite simple de peticiones por IP (token bucket) para los endpoints publicos
 * de validacion y activacion. Protege frente a intentos automatizados y abuso.
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int capacity;
    private final long refillMillis;
    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(LicensingProperties properties, ObjectMapper objectMapper) {
        this.enabled = properties.getRateLimit().isEnabled();
        this.capacity = properties.getRateLimit().getCapacity();
        this.refillMillis = properties.getRateLimit().getRefillSeconds() * 1000L;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!enabled || !aplica(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String ip = clienteIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> new Bucket(capacity, refillMillis));
        if (!bucket.tryConsume()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(),
                    ErrorResponse.negocio("DEMASIADAS_PETICIONES", "Demasiadas peticiones. Intente mas tarde."));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean aplica(String uri) {
        return uri.startsWith("/api/instalador") || uri.startsWith("/api/activacion");
    }

    private String clienteIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Token bucket con recarga lineal. */
    private static final class Bucket {
        private final int capacity;
        private final long refillMillis;
        private double tokens;
        private long lastRefill;

        Bucket(int capacity, long refillMillis) {
            this.capacity = capacity;
            this.refillMillis = refillMillis;
            this.tokens = capacity;
            this.lastRefill = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            double elapsed = (now - lastRefill) / 1_000_000.0;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + (elapsed / refillMillis) * capacity);
                lastRefill = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
