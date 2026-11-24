package com.jitong.core.config;

import com.jitong.core.employee.EmployeeRepository;
import com.jitong.core.security.AuthCookie;
import com.jitong.core.security.JwtAuthenticationFilter;
import com.jitong.core.security.JwtService;
import com.jitong.core.security.OriginCheckFilter;
import com.jitong.core.security.SecurityErrorWriter;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JitongProperties properties;
    private final JwtService jwtService;
    private final EmployeeRepository employeeRepository;
    private final AuthCookie authCookie;
    private final SecurityErrorWriter errorWriter;
    private final Clock clock;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(jwtService, employeeRepository, authCookie, clock);
        OriginCheckFilter originFilter = new OriginCheckFilter(properties);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                errorWriter.write(response, 401, "unauthorized", "请先登录"))
                        .accessDeniedHandler((request, response, ex) ->
                                errorWriter.write(response, 403, "forbidden", "没有权限执行该操作")))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(policy -> policy
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/", "/index.html", "/*.html", "/*.js", "/*.css", "/*.png",
                                "/*.webmanifest", "/manifest.webmanifest", "/sw.js",
                                "/vendor/**", "/icons/**", "/favicon.ico", "/error"
                        ).permitAll()
                        .requestMatchers(
                                "/api/health", "/api/login", "/api/auth/refresh",
                                "/api/initial-accounts", "/api/docs", "/api/docs/**",
                                "/api/swagger-ui/**", "/api/swagger-ui.html",
                                "/api/openapi.json", "/swagger-ui/**", "/swagger-ui.html",
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(originFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(properties.getCors().getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (!origins.isEmpty()) {
            configuration.setAllowedOrigins(origins);
            configuration.setAllowCredentials(true);
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("*"));
            configuration.setExposedHeaders(List.of(HttpHeaders.SET_COOKIE));
            configuration.setMaxAge(3600L);
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!origins.isEmpty()) {
            source.registerCorsConfiguration("/**", configuration);
        }
        return source;
    }
}
