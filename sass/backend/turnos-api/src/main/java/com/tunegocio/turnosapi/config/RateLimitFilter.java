package com.tunegocio.turnosapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunegocio.turnosapi.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting en memoria por IP y categoría de endpoint.
 * Está pensado para una sola instancia; en multi-nodo debería moverse a Redis.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    private final Map<String, SlidingWindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        RateLimitPolicy policy = resolvePolicy(request);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = policy.bucketKey() + ":" + resolveClientIp(request);
        SlidingWindowCounter counter = counters.computeIfAbsent(
                key, ignored -> new SlidingWindowCounter(policy.limit(), policy.window())
        );

        SlidingWindowDecision decision = counter.tryConsume();
        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));

            ErrorResponse body = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                    .message("Demasiadas solicitudes para este endpoint. Intente nuevamente en unos segundos.")
                    .path(request.getRequestURI())
                    .build();

            objectMapper.writeValue(response.getWriter(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitPolicy resolvePolicy(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (HttpMethod.POST.matches(method) && "/api/auth/login".equals(path)) {
            return new RateLimitPolicy("auth-login", 5, Duration.ofMinutes(1));
        }
        if (HttpMethod.POST.matches(method) && "/api/auth/register".equals(path)) {
            return new RateLimitPolicy("auth-register", 3, Duration.ofMinutes(10));
        }
        if (HttpMethod.POST.matches(method) && "/api/auth/refresh".equals(path)) {
            return new RateLimitPolicy("auth-refresh", 10, Duration.ofMinutes(5));
        }
        if (HttpMethod.POST.matches(method) && path.matches("^/public/[^/]+/reservar$")) {
            return new RateLimitPolicy("public-booking", 20, Duration.ofMinutes(1));
        }
        if (HttpMethod.POST.matches(method) && "/api/staff/invitaciones/aceptar".equals(path)) {
            return new RateLimitPolicy("staff-accept-invitation", 10, Duration.ofMinutes(10));
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private record RateLimitPolicy(String bucketKey, int limit, Duration window) {
    }

    private record SlidingWindowDecision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class SlidingWindowCounter {
        private final int limit;
        private final Duration window;
        private final Deque<Long> timestamps = new ArrayDeque<>();

        private SlidingWindowCounter(int limit, Duration window) {
            this.limit = limit;
            this.window = window;
        }

        private synchronized SlidingWindowDecision tryConsume() {
            long nowMillis = System.currentTimeMillis();
            long windowStart = nowMillis - window.toMillis();

            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.removeFirst();
            }

            if (timestamps.size() >= limit) {
                long oldest = timestamps.peekFirst();
                long retryAfterMillis = Math.max(1, window.toMillis() - (nowMillis - oldest));
                long retryAfterSeconds = Math.max(1, Duration.ofMillis(retryAfterMillis).getSeconds());
                return new SlidingWindowDecision(false, retryAfterSeconds);
            }

            timestamps.addLast(nowMillis);
            return new SlidingWindowDecision(true, 0);
        }
    }
}
