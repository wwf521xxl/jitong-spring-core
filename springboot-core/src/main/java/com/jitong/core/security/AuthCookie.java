package com.jitong.core.security;

import com.jitong.core.config.JitongProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookie {

    private final JitongProperties properties;

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = properties.getCookie().getName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void write(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie
                .from(properties.getCookie().getName(), token)
                .httpOnly(true)
                .secure(properties.getCookie().isSecure())
                .sameSite(properties.getCookie().getSameSite())
                .path("/")
                .maxAge(properties.getJwt().getRefreshTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from(properties.getCookie().getName(), "")
                .httpOnly(true)
                .secure(properties.getCookie().isSecure())
                .sameSite(properties.getCookie().getSameSite())
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
