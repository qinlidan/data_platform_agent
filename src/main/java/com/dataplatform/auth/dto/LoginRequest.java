package com.dataplatform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名或邮箱", example = "admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "280976")
    private String password;

    @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
    @Schema(description = "验证码（6位数字，可选）", example = "123456")
    private String code;  // 可选

    // 兼容旧的 email 字段
    @Schema(hidden = true)
    private String email;
}
