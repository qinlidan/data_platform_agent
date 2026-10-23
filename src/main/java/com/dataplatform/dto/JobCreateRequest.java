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

/**
 * 作业创建请求DTO
 */
@Data
public class JobCreateRequest {

    @NotBlank(message = "作业名称不能为空")
    @Pattern(regexp = "^test_[a-zA-Z0-9_]+$", message = "作业名称必须以test_开头，且只能包含英文、数字和下划线")
    private String jobName;

    @NotBlank(message = "作业中文名称不能为空")
    private String jobCnName;

    @NotNull(message = "作业类型不能为空")
    @EnumValue(enumClass = JobType.class, message = "作业类型不合法，必须是 DB2HIVE 或 HIVE2HIVE")
    private JobType jobType;

    // HIVE2HIVE 无源数据源，可为空；DB2HIVE 必填（由 service 层校验）
    private Long sourceDatasourceId;

    @NotBlank(message = "源库名不能为空")
    private String sourceDatabase;

    @NotBlank(message = "源表名不能为空")
    private String sourceTable;

    @NotBlank(message = "目标库名不能为空")
    private String targetDatabase;

    @NotBlank(message = "目标表名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "目标表名只能包含英文、数字和下划线")
    private String targetTable;

    @NotNull(message = "执行引擎不能为空")
    @EnumValue(enumClass = EngineType.class, message = "执行引擎不合法，必须是 HIVE 或 SPARK")
    private EngineType engineType;

    private String description;

    /**
     * 调度频率：DAILY/WEEKLY/MONTHLY/YEARLY
     */
    @EnumValue(enumClass = ScheduleFrequency.class, nullable = true, message = "调度频率不合法，必须是 DAILY/WEEKLY/MONTHLY/YEARLY")
    private ScheduleFrequency scheduleFrequency;

    /**
     * 是否监控该作业（默认为true）
     */
    @NotNull(message = "是否监控不能为空")
    @JsonProperty("monitored")
    private Boolean monitored;
}
