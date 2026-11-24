package com.jitong.core.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "请输入工号") String account,
        @NotBlank(message = "请输入 PIN / 密码") String pin
) {
}
