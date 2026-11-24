package com.jitong.core.security;

import com.jitong.core.audit.AuditService;
import com.jitong.core.bootstrap.BootstrapResponse;
import com.jitong.core.bootstrap.BootstrapService;
import com.jitong.core.common.ApiException;
import com.jitong.core.config.JitongProperties;
import com.jitong.core.employee.Employee;
import com.jitong.core.employee.EmployeeRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final EmployeeRepository employeeRepository;
    private final com.jitong.core.employee.EmployeeService employeeService;
    private final PinHasher pinHasher;
    private final JwtService jwtService;
    private final AuthCookie authCookie;
    private final LoginAttemptService loginAttemptService;
    private final BootstrapService bootstrapService;
    private final AuditService auditService;
    private final JitongProperties properties;
    private final Clock clock;

    @Transactional
    public BootstrapResponse login(LoginRequest request, String clientIp, HttpServletResponse response) {
        String account = request.account() == null ? "" : request.account().trim();
        String key = account.toLowerCase() + "|" + clientIp;
        loginAttemptService.check(key);
        Employee employee = employeeService.findActiveByAccount(account).orElse(null);
        if (employee == null || !pinHasher.matches(request.pin(), employee.getPinHash())) {
            loginAttemptService.failure(key);
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "bad_credentials", "工号或密码不正确");
        }
        loginAttemptService.success(key);
        String refresh = jwtService.refreshToken(employee);
        authCookie.write(response, refresh);
        auditService.record(employee.getId(), "login", "employee", employee.getId(), "");
        BootstrapResponse bootstrap = bootstrapService.build(UserPrincipal.from(employee));
        return bootstrapService.withToken(bootstrap, jwtService.accessToken(employee));
    }

    @Transactional
    public void logout(UserPrincipal principal, HttpServletResponse response) {
        Employee employee = employeeRepository.findById(principal.id())
                .orElseThrow(() -> ApiException.unauthorized("请先登录"));
        employee.setTokenVersion(employee.getTokenVersion() + 1);
        employee.setUpdatedAt(Instant.now(clock));
        employeeRepository.save(employee);
        authCookie.clear(response);
        auditService.record(employee.getId(), "logout", "employee", employee.getId(), "");
    }

    public Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = authCookie.read(request);
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        try {
            Claims claims = jwtService.parse(token);
            if (!JwtService.TYPE_REFRESH.equals(claims.get("typ", String.class))) {
                throw ApiException.unauthorized("登录已过期，请重新登录");
            }
            Employee employee = employeeRepository.findById(claims.getSubject())
                    .filter(Employee::isActive)
                    .orElseThrow(() -> ApiException.unauthorized("登录已过期，请重新登录"));
            Integer version = claims.get("ver", Integer.class);
            if (version == null || version != employee.getTokenVersion()) {
                throw ApiException.unauthorized("登录已过期，请重新登录");
            }
            String access = jwtService.accessToken(employee);
            return Map.of("token", access, "expiresIn", properties.getJwt().getAccessTtl().toSeconds());
        } catch (JwtException | IllegalArgumentException ex) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
    }

    @Transactional
    public Map<String, Object> changePassword(
            UserPrincipal principal,
            PasswordCommand command,
            HttpServletResponse response
    ) {
        Employee employee = employeeRepository.findById(principal.id())
                .orElseThrow(() -> ApiException.unauthorized("请先登录"));
        if (!pinHasher.matches(command.old(), employee.getPinHash())) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "wrong_password", "原密码不正确");
        }
        String newPassword = command.newPassword() == null ? "" : command.newPassword();
        if (newPassword.length() < 6) {
            throw ApiException.badRequest("weak_password", "新密码至少 6 位");
        }
        employee.setPinHash(pinHasher.hash(newPassword));
        employee.setTokenVersion(employee.getTokenVersion() + 1);
        employee.setUpdatedAt(Instant.now(clock));
        employeeRepository.save(employee);
        authCookie.write(response, jwtService.refreshToken(employee));
        auditService.record(employee.getId(), "change_password", "employee", employee.getId(), "");
        return Map.of("ok", true, "token", jwtService.accessToken(employee));
    }
}
