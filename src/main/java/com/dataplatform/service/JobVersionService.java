package com.dataplatform.service;

import com.dataplatform.dto.SqlConfigRequest;
import com.dataplatform.entity.JobVersion;

import java.util.List;

/**
 * 作业版本服务接口
 */
public interface JobVersionService {

    /**
     * 配置SQL
     */
    JobVersion configureSql(Long jobId, SqlConfigRequest request);

    /**
     * 根据作业ID查询所有版本
     */
    List<JobVersion> listByJobId(Long jobId);

    /**
     * 根据ID查询版本详情
     */
    JobVersion getById(Long id);

    /**
     * 发布版本
     */
    void publish(Long id);

    /**
     * 删除版本
     */
    void delete(Long id);
}
