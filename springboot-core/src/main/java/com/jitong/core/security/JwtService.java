package com.jitong.core.security;

import com.jitong.core.config.JitongProperties;
import com.jitong.core.employee.Employee;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JitongProperties properties;
    private final SecretKey key;
    private final Clock clock;

    public JwtService(JitongProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] secret = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JITONG_JWT_SECRET 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        if (properties.getJwt().getSecret().contains("dev-secret")) {
            log.warn("正在使用开发用 JWT 密钥；生产环境请设置 JITONG_JWT_SECRET");
        }
    }

    public String issue(Employee employee, String type, Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(employee.getId())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim("typ", type)
                .claim("ver", employee.getTokenVersion())
                .claim("name", employee.getName())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String accessToken(Employee employee) {
        return issue(employee, TYPE_ACCESS, properties.getJwt().getAccessTtl());
    }

    public String refreshToken(Employee employee) {
        return issue(employee, TYPE_REFRESH, properties.getJwt().getRefreshTtl());
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getJwt().getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
