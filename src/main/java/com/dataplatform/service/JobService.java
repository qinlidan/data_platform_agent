package com.dataplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.dto.JobCreateRequest;
import com.dataplatform.dto.JobQueryRequest;
import com.dataplatform.dto.JobUpdateRequest;
import com.dataplatform.entity.Job;

/**
 * 作业服务接口
 */
public interface JobService {

    /**
     * 创建作业
     */
    Job create(JobCreateRequest request, String creatorUserId);

    /**
     * 更新作业（仅创建者可操作）
     */
    Job update(Long id, JobUpdateRequest request, String operatorUserId);

    /**
     * 删除作业（仅创建者可操作）
     */
    void delete(Long id, String operatorUserId);

    /**
     * 根据ID查询作业
     */
    Job getById(Long id);

    /**
     * 分页查询作业列表
     */
    Page<Job> page(JobQueryRequest request);

    /**
     * 发布作业（仅创建者可操作）
     */
    void publish(Long id, Integer version, String operatorUserId);

    /**
     * 归档作业（仅创建者可操作）
     */
    void archive(Long id, String operatorUserId);
}
