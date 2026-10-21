package com.dataplatform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SQL配置请求DTO
 */
@Data
public class SqlConfigRequest {

    @NotBlank(message = "SQL内容不能为空")
    private String sqlContent;

    private String sparkConfig;

    private String changeLog;
}
