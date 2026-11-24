package com.jitong.core.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jitong")
@Getter
@Setter
public class JitongProperties {

    private String dataDir = "./data";
    private int bootstrapDays = 45;
    private boolean showInitialAccounts = true;

    private Jwt jwt = new Jwt();
    private CookieSettings cookie = new CookieSettings();
    private Seed seed = new Seed();
    private SecuritySettings security = new SecuritySettings();
    private Cors cors = new Cors();

    @Getter
    @Setter
    public static class Jwt {
        private String secret = "jitong-spring-dev-secret-please-override-in-production-2026";
        private String issuer = "jitong-spring-core";
        private Duration accessTtl = Duration.ofMinutes(30);
        private Duration refreshTtl = Duration.ofDays(30);
    }

    @Getter
    @Setter
    public static class CookieSettings {
        private String name = "jitong_session";
        private boolean secure = false;
        private String sameSite = "Lax";
    }

    @Getter
    @Setter
    public static class Seed {
        private boolean demo = true;
        private String adminPassword = "Admin@123456";
        private String employeePin = "123456";
    }

    @Getter
    @Setter
    public static class SecuritySettings {
        private int loginMaxAttempts = 20;
        private Duration loginWindow = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class Cors {
        /** 逗号分隔；为空表示只允许同源。前端与后端分开部署时才需要配置。 */
        private String allowedOrigins = "";
    }
}
