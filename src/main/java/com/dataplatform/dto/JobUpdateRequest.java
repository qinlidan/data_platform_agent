package com.dataplatform.dto;

import com.dataplatform.enums.EngineType;
import com.dataplatform.enums.JobType;
import lombok.Data;

/**
 * 作业更新请求DTO
 */
@Data
public class JobUpdateRequest {

    private String jobName;

    private JobType jobType;

    private Long sourceDatasourceId;

    private String sourceDatabase;

    private String sourceTable;

    private String targetDatabase;

    private String targetTable;

    private EngineType engineType;

    private String description;
}
