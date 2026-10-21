package com.dataplatform.dto;

import com.dataplatform.enums.DataSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据源请求DTO
 */
@Data
public class DataSourceRequest {

    @NotBlank(message = "数据源编码不能为空")
    private String sourceCode;

    @NotBlank(message = "数据源名称不能为空")
    private String sourceName;

    @NotNull(message = "数据源类型不能为空")
    private DataSourceType sourceType;

    private String driver;

    @NotBlank(message = "JDBC URL不能为空")
    private String jdbcUrl;

    private String configInfo;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String description;
}
