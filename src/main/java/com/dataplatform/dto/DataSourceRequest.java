package com.dataplatform.dto;

import com.dataplatform.enums.DataSourceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 数据源请求DTO
 */
@Data
public class DataSourceRequest {

    @ToolParam(description = "数据源编码，全局唯一标识")
    @JsonProperty(required = true, value = "sourceCode")
    @NotBlank(message = "数据源编码不能为空")
    private String sourceCode;

    @ToolParam(description = "数据源名称")
    @JsonProperty(required = true, value = "sourceName")
    @NotBlank(message = "数据源名称不能为空")
    private String sourceName;

    @ToolParam(description = "数据源类型：MYSQL | POSTGRESQL | ORACLE | SQLSERVER | DB2 | HIVE")
    @JsonProperty(required = true, value = "sourceType")
    @NotNull(message = "数据源类型不能为空")
    private DataSourceType sourceType;

    @ToolParam(description = "JDBC驱动类名，可选")
    @JsonProperty(required = false, value = "driver")
    private String driver;

    @ToolParam(description = "JDBC连接URL，例如 jdbc:mysql://localhost:3306/test")
    @JsonProperty(required = true, value = "jdbcUrl")
    @NotBlank(message = "JDBC URL不能为空")
    private String jdbcUrl;

    @ToolParam(description = "额外配置信息，可选")
    @JsonProperty(required = false, value = "configInfo")
    private String configInfo;

    @ToolParam(description = "数据库用户名")
    @JsonProperty(required = true, value = "username")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @ToolParam(description = "数据库密码")
    @JsonProperty(required = true, value = "password")
    @NotBlank(message = "密码不能为空")
    private String password;

    @ToolParam(description = "数据源描述，可选")
    @JsonProperty(required = false, value = "description")
    private String description;
}
