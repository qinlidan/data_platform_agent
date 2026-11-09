package com.dataplatform.dto;

import com.dataplatform.enums.JobStatus;
import com.dataplatform.enums.JobType;
import com.dataplatform.enums.ScheduleFrequency;
import lombok.Data;

/**
 * 作业查询请求DTO
 */
@Data
public class JobQueryRequest {

    /**
     * 页码，默认1
     */
    private Integer pageNum = 1;

    /**
     * 每页大小，默认10
     */
    private Integer pageSize = 10;

    /**
     * 作业类型：DB2HIVE、HIVE2HIVE
     */
    private JobType jobType;

    /**
     * 作业状态：DRAFT、PUBLISHED、ARCHIVED
     */
    private JobStatus status;

    /**
     * 源数据源ID
     */
    private Long sourceDatasourceId;

    /**
     * 源库名
     */
    private String sourceDatabase;

    /**
     * 源表名
     */
    private String sourceTable;

    /**
     * 调度频率：DAILY、WEEKLY、MONTHLY、YEARLY
     */
    private ScheduleFrequency scheduleFrequency;

    /**
     * 作业名称（支持右模糊查询）
     */
    private String jobName;

    /**
     * 作业中文名称（支持右模糊查询）
     */
    private String jobCnName;
}
