package com.jitong.core.security;

import com.jitong.core.employee.Employee;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record UserPrincipal(
        String id,
        int employeeNo,
        String name,
        String role,
        String avatar,
        boolean admin,
        boolean canCreateJob,
        int tokenVersion
) implements Principal {

    public static UserPrincipal from(Employee employee) {
        return new UserPrincipal(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getName(),
                employee.getRole(),
                employee.getAvatar(),
                employee.isAdmin(),
                employee.isCanCreateJob(),
                employee.getTokenVersion()
        );
    }

    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new ArrayList<>(3);
        authorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        if (admin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        if (canCreateJob) {
            authorities.add(new SimpleGrantedAuthority("ROLE_CREATOR"));
        }
        return List.copyOf(authorities);
    }

    @Override
    public String getName() {
        return id;
    }
}
