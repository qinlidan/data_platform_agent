package com.dataplatform.auth.dto;

import com.dataplatform.auth.enums.CodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "发送验证码请求")
public class SendCodeRequest {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱地址", example = "user@example.com")
    private String email;

    @NotNull(message = "验证码类型不能为空")
    @Schema(description = "验证码类型", example = "REGISTER")
    private CodeType type;
}
