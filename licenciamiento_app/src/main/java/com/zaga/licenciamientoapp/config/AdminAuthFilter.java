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
import java.security.MessageDigest;

/**
 * Exige la cabecera {@code X-Admin-Api-Key} para todas las operaciones bajo
 * {@code /api/admin/**}. La clave se lee de {@code licensing.admin-api-key}
 * (variable de entorno ADMIN_API_KEY en produccion).
 */
@Component
@Order(1)
public class AdminAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Api-Key";

    private final String adminApiKey;
    private final ObjectMapper objectMapper;

    public AdminAuthFilter(LicensingProperties properties, ObjectMapper objectMapper) {
        this.adminApiKey = properties.getAdminApiKey();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/api/admin")) {
            chain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (adminApiKey == null || adminApiKey.isBlank() || !claveValida(provided)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(),
                    ErrorResponse.negocio("NO_AUTORIZADO", "Credencial administrativa ausente o invalida."));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean claveValida(String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                adminApiKey.getBytes(StandardCharsets.UTF_8));
    }
}
