package com.jitong.core.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordCommand(
        @NotBlank(message = "请输入原密码") String old,
        @JsonProperty("new") @NotBlank(message = "请输入新密码")
        @Size(min = 6, message = "新密码至少 6 位") String newPassword
) {
}
