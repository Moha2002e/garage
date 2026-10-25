package com.garage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cookie cookie,
        String frontUrl,
        String corsOrigin,
        String mailFrom,
        int resetTokenMinutes,
        int exportMaxRows,
        Admin admin) {

    public record Jwt(String secret, int sessionHours, int rememberDays) {
    }

    public record Cookie(String name, boolean secure) {
    }

    public record Admin(String email, String password) {
    }
}
