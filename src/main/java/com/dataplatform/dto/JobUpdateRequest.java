package com.dataplatform.dto;

import com.dataplatform.enums.EngineType;
import com.dataplatform.enums.JobType;
import com.dataplatform.enums.ScheduleFrequency;
import com.dataplatform.validator.EnumValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 作业更新请求DTO
 */
@Data
public class JobUpdateRequest {

    private String jobCnName;

    @EnumValue(enumClass = JobType.class, nullable = true, message = "作业类型不合法")
    private JobType jobType;

    private Long sourceDatasourceId;

    private String sourceDatabase;

    private String sourceTable;

    private String targetDatabase;

    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "目标表名只能包含英文、数字和下划线")
    private String targetTable;

    @EnumValue(enumClass = EngineType.class, nullable = true, message = "执行引擎不合法")
    private EngineType engineType;

    private String description;

    @EnumValue(enumClass = ScheduleFrequency.class, nullable = true, message = "调度频率不合法")
    private ScheduleFrequency scheduleFrequency;

    @JsonProperty("monitored")
    private Boolean monitored;
}
