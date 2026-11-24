package com.jitong.core.security;

import com.jitong.core.bootstrap.BootstrapResponse;
import com.jitong.core.bootstrap.BootstrapService;
import com.jitong.core.employee.EmployeeView;
import com.jitong.core.employee.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final BootstrapService bootstrapService;
    private final EmployeeService employeeService;

    @PostMapping("/api/login")
    public BootstrapResponse login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return authService.login(body, clientIp(request), response);
    }

    @PostMapping("/api/logout")
    public Map<String, Object> logout(
            @AuthenticationPrincipal UserPrincipal user,
            HttpServletResponse response
    ) {
        authService.logout(user, response);
        return Map.of("ok", true);
    }

    @PostMapping("/api/auth/refresh")
    public Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        return authService.refresh(request, response);
    }

    @GetMapping("/api/bootstrap")
    public BootstrapResponse bootstrap(@AuthenticationPrincipal UserPrincipal user) {
        return bootstrapService.build(user);
    }

    @GetMapping("/api/me")
    public EmployeeView me(@AuthenticationPrincipal UserPrincipal user) {
        return employeeService.view(employeeService.require(user.id()));
    }

    @PostMapping("/api/change-password")
    public Map<String, Object> changePassword(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody PasswordCommand body,
            HttpServletResponse response
    ) {
        return authService.changePassword(user, body, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
