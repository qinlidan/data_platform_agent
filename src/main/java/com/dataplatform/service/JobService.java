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
    Job create(JobCreateRequest request);

    /**
     * 更新作业
     */
    Job update(Long id, JobUpdateRequest request);

    /**
     * 删除作业
     */
    void delete(Long id);

    /**
     * 根据ID查询作业
     */
    Job getById(Long id);

    /**
     * 根据作业编码查询作业
     */
    Job getByCode(String jobCode);

    /**
     * 分页查询作业列表
     */
    Page<Job> page(JobQueryRequest request);

    /**
     * 发布作业
     */
    void publish(Long id, Integer version);

    /**
     * 归档作业
     */
    void archive(Long id);
}
