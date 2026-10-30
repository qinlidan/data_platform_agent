package com.dataplatform.dto;

import com.dataplatform.enums.EngineType;
import com.dataplatform.enums.JobType;
import com.dataplatform.enums.ScheduleFrequency;
import com.dataplatform.validator.EnumValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 作业创建请求DTO
 */
@Data
public class JobCreateRequest {

    @ToolParam(description = "作业名称（英文标识），必须以test_开头")
    @JsonProperty(required = true, value = "jobName")
    @NotBlank(message = "作业名称不能为空")
    @Pattern(regexp = "^test_[a-zA-Z0-9_]+$", message = "作业名称必须以test_开头，且只能包含英文、数字和下划线")
    private String jobName;

    @ToolParam(description = "作业中文名称")
    @JsonProperty(required = true, value = "jobCnName")
    @NotBlank(message = "作业中文名称不能为空")
    private String jobCnName;

    @ToolParam(description = "作业类型：DB2HIVE（数据库到Hive） | HIVE2HIVE（Hive到Hive）")
    @JsonProperty(required = true, value = "jobType")
    @NotNull(message = "作业类型不能为空")
    @EnumValue(enumClass = JobType.class, message = "作业类型不合法，必须是 DB2HIVE 或 HIVE2HIVE")
    private JobType jobType;

    @ToolParam(description = "源数据源ID（DB2HIVE类型必填，HIVE2HIVE可为空）")
    @JsonProperty(required = false, value = "sourceDatasourceId")
    // HIVE2HIVE 无源数据源，可为空；DB2HIVE 必填（由 service 层校验）
    private Long sourceDatasourceId;

    @ToolParam(description = "源数据库名")
    @JsonProperty(required = true, value = "sourceDatabase")
    @NotBlank(message = "源库名不能为空")
    private String sourceDatabase;

    @ToolParam(description = "源表名")
    @JsonProperty(required = true, value = "sourceTable")
    @NotBlank(message = "源表名不能为空")
    private String sourceTable;

    @ToolParam(description = "目标数据库名")
    @JsonProperty(required = true, value = "targetDatabase")
    @NotBlank(message = "目标库名不能为空")
    private String targetDatabase;

    @ToolParam(description = "目标表名")
    @JsonProperty(required = true, value = "targetTable")
    @NotBlank(message = "目标表名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "目标表名只能包含英文、数字和下划线")
    private String targetTable;

    @ToolParam(description = "执行引擎：HIVE | SPARK")
    @JsonProperty(required = true, value = "engineType")
    @NotNull(message = "执行引擎不能为空")
    @EnumValue(enumClass = EngineType.class, message = "执行引擎不合法，必须是 HIVE 或 SPARK")
    private EngineType engineType;

    @ToolParam(description = "作业描述，可选")
    @JsonProperty(required = false, value = "description")
    private String description;

    @ToolParam(description = "调度频率：DAILY（每日） | WEEKLY（每周） | MONTHLY（每月） | YEARLY（每年），可选")
    @JsonProperty(required = false, value = "scheduleFrequency")
    /**
     * 调度频率：DAILY/WEEKLY/MONTHLY/YEARLY
     */
    @EnumValue(enumClass = ScheduleFrequency.class, nullable = true, message = "调度频率不合法，必须是 DAILY/WEEKLY/MONTHLY/YEARLY")
    private ScheduleFrequency scheduleFrequency;

    @ToolParam(description = "是否监控该作业，默认为true")
    @JsonProperty(required = false, value = "monitored")
    /**
     * 是否监控该作业（默认为true）
     */
    @NotNull(message = "是否监控不能为空")
    private Boolean monitored;
}
