package com.jitong.core.security;

import com.jitong.core.config.JitongProperties;
import com.jitong.core.employee.Employee;
import com.jitong.core.employee.EmployeeRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 同时支持两种客户端：
 * - 现有 H5/PWA：HttpOnly Cookie（refresh token，30 天，自动续期）
 * - 第三方/接口调用：Authorization: Bearer <access token>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_VIA_COOKIE = "jitong.auth.viaCookie";

    private final JwtService jwtService;
    private final EmployeeRepository employeeRepository;
    private final AuthCookie authCookie;
    private final Clock clock;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            EmployeeRepository employeeRepository,
            AuthCookie authCookie,
            Clock clock
    ) {
        this.jwtService = jwtService;
        this.employeeRepository = employeeRepository;
        this.authCookie = authCookie;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String bearer = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                ? header.substring(7).trim()
                : null;
        String cookieToken = authCookie.read(request);
        boolean viaCookie = (bearer == null || bearer.isBlank()) && cookieToken != null;

        String token = bearer != null && !bearer.isBlank() ? bearer : cookieToken;
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parse(token);
            String type = claims.get("typ", String.class);
            if (!viaCookie && !JwtService.TYPE_ACCESS.equals(type)) {
                filterChain.doFilter(request, response);
                return;
            }
            if (viaCookie && !JwtService.TYPE_REFRESH.equals(type)) {
                filterChain.doFilter(request, response);
                return;
            }

            Employee employee = employeeRepository.findById(claims.getSubject())
                    .filter(Employee::isActive)
                    .orElse(null);
            Integer version = claims.get("ver", Integer.class);
            if (employee == null || version == null || version != employee.getTokenVersion()) {
                filterChain.doFilter(request, response);
                return;
            }

            UserPrincipal principal = UserPrincipal.from(employee);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (viaCookie) {
                request.setAttribute(AUTH_VIA_COOKIE, Boolean.TRUE);
                // 滑动续期：临近过期时换一张新的 refresh cookie，前端无需感知。
                if (claims.getExpiration().toInstant().isBefore(clock.instant().plusSeconds(86_400))) {
                    authCookie.write(response, jwtService.refreshToken(employee));
                }
            }
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
