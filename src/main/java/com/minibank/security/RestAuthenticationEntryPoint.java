package com.minibank.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.dto.common.ApiError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 401 чыгаруучу — REST үчүн JSON (HTML эмес).
 *
 * 🧠 Эмне бул керек?
 *     Spring Security демкилгизде HTML бетин ("логин барагы")
 *     кайтарат. Биз REST API жасап жатабыз — клиент бизге
 *     "кошумча маалымат" деп JSON күтөт. Ошондуктан өзүбүздүн
 *     барагыбызды жазабыз — ал биздин ApiError форматын колдонот.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Authentication required. Please provide a valid Bearer token.",
                request.getRequestURI(),
                java.util.List.of());

        objectMapper.writeValue(response.getWriter(), error);
    }
}
