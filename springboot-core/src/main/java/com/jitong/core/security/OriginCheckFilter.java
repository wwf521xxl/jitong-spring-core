package com.jitong.core.security;

import com.jitong.core.config.JitongProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 现有前端用 Cookie 认证，且没有 CSRF Token。
 * 这里对写请求校验 Origin：同源放行；跨源必须出现在显式白名单中。
 * 这比直接关闭 CSRF 更安全，也不需要改前端一行代码。
 */
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name(), "TRACE"
    );

    private final Set<String> allowedOrigins;

    public OriginCheckFilter(JitongProperties properties) {
        this.allowedOrigins = Arrays.stream(properties.getCors().getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        boolean cookieAuth = Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.AUTH_VIA_COOKIE));
        if (!cookieAuth || SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank() || isSameOrigin(origin, request) || allowedOrigins.contains(origin)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"detail\":{\"code\":\"csrf_origin\",\"message\":\"请求来源不被允许\"}}");
    }

    private boolean isSameOrigin(String origin, HttpServletRequest request) {
        try {
            URI uri = URI.create(origin);
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort(uri.getScheme());
            int requestPort = request.getServerPort();
            int expectedPort = requestPort > 0 ? requestPort : defaultPort(request.getScheme());
            return equalsIgnoreCase(uri.getScheme(), request.getScheme())
                    && equalsIgnoreCase(uri.getHost(), request.getServerName())
                    && port == expectedPort;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
