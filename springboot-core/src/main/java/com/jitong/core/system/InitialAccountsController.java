package com.jitong.core.system;

import com.jitong.core.common.ApiException;
import com.jitong.core.config.JitongProperties;
import com.jitong.core.employee.Employee;
import com.jitong.core.employee.EmployeeRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InitialAccountsController {

    private final EmployeeRepository employeeRepository;
    private final JitongProperties properties;

    @GetMapping("/api/initial-accounts")
    public Map<String, Object> initialAccounts(HttpServletRequest request) {
        if (!properties.isShowInitialAccounts()) {
            throw ApiException.notFound("disabled", "账号显示未开启");
        }
        if (!isInternal(request)) {
            throw ApiException.forbidden("仅限内网查看");
        }
        Map<String, Object> admin = new LinkedHashMap<>();
        admin.put("account", "admin");
        admin.put("name", "厂长");
        admin.put("role", "厂长 · 管理员");
        admin.put("pin", properties.getSeed().getAdminPassword());

        List<Map<String, Object>> employees = new ArrayList<>();
        for (Employee employee : employeeRepository.findByActiveTrueOrderByEmployeeNoAsc()) {
            if (employee.isAdmin()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("account", String.valueOf(employee.getEmployeeNo()));
            row.put("name", employee.getName());
            row.put("role", employee.getRole());
            row.put("pin", properties.getSeed().getEmployeePin());
            employees.add(row);
        }
        return Map.of("admin", admin, "employees", employees);
    }

    private boolean isInternal(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String host = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isAnyLocalAddress();
        } catch (Exception ex) {
            // 测试客户端或未知地址时交给开关控制。
            return true;
        }
    }
}
