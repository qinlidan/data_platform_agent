package com.dataplatform.dto;

import com.dataplatform.enums.EngineType;
import com.dataplatform.enums.JobType;
import com.dataplatform.enums.ScheduleFrequency;
import com.dataplatform.validator.EnumValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 作业更新请求DTO
 */
@Data
public class JobUpdateRequest {

    @ToolParam(description = "新的作业中文名称，可选")
    @JsonProperty(required = false, value = "jobCnName")
    private String jobCnName;

    @ToolParam(description = "新的作业类型：DB2HIVE | HIVE2HIVE，可选")
    @JsonProperty(required = false, value = "jobType")
    @EnumValue(enumClass = JobType.class, nullable = true, message = "作业类型不合法")
    private JobType jobType;

    @ToolParam(description = "新的源数据源ID，可选")
    @JsonProperty(required = false, value = "sourceDatasourceId")
    private Long sourceDatasourceId;

    @ToolParam(description = "新的源数据库名，可选")
    @JsonProperty(required = false, value = "sourceDatabase")
    private String sourceDatabase;

    @ToolParam(description = "新的源表名，可选")
    @JsonProperty(required = false, value = "sourceTable")
    private String sourceTable;

    @ToolParam(description = "新的目标数据库名，可选")
    @JsonProperty(required = false, value = "targetDatabase")
    private String targetDatabase;

    @ToolParam(description = "新的目标表名，可选")
    @JsonProperty(required = false, value = "targetTable")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "目标表名只能包含英文、数字和下划线")
    private String targetTable;

    @ToolParam(description = "新的执行引擎：HIVE | SPARK，可选")
    @JsonProperty(required = false, value = "engineType")
    @EnumValue(enumClass = EngineType.class, nullable = true, message = "执行引擎不合法")
    private EngineType engineType;

    @ToolParam(description = "新的作业描述，可选")
    @JsonProperty(required = false, value = "description")
    private String description;

    @ToolParam(description = "新的调度频率：DAILY | WEEKLY | MONTHLY | YEARLY，可选")
    @JsonProperty(required = false, value = "scheduleFrequency")
    @EnumValue(enumClass = ScheduleFrequency.class, nullable = true, message = "调度频率不合法")
    private ScheduleFrequency scheduleFrequency;

    @ToolParam(description = "是否监控该作业，可选")
    @JsonProperty(required = false, value = "monitored")
    private Boolean monitored;
}
