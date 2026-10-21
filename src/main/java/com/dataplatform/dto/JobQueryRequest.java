package com.dataplatform.dto;

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
     * 搜索关键词（作业编码或名称）
     */
    private String keyword;

    /**
     * 作业类型：DB2HIVE、HIVE2HIVE
     */
    private String jobType;

    /**
     * 作业状态：DRAFT、PUBLISHED、ARCHIVED
     */
    private String status;

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
     * 作业频率
     */
    private String scheduleFrequency;
}
