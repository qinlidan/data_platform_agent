package com.dataplatform.dto;

import com.dataplatform.enums.EngineType;
import com.dataplatform.enums.JobType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 作业创建请求DTO
 */
@Data
public class JobCreateRequest {

    @NotBlank(message = "作业编码不能为空")
    private String jobCode;

    @NotBlank(message = "作业名称不能为空")
    private String jobName;

    @NotNull(message = "作业类型不能为空")
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
    private String targetTable;

    @NotNull(message = "执行引擎不能为空")
    private EngineType engineType;

    private String description;

    /**
     * 调度频率：DAILY/MONTHLY/HOURLY/WEEKLY
     */
    private String scheduleFrequency;
}
