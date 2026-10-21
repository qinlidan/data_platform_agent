package com.dataplatform.service;

import com.dataplatform.dto.JobOperationRequest;
import com.dataplatform.dto.JobOperationResponse;

/**
 * 自然语言作业管理服务接口
 */
public interface NaturalLanguageJobService {

    /**
     * 自然语言对话
     */
    JobOperationResponse chat(JobOperationRequest request);

    /**
     * 自然语言创建作业
     */
    JobOperationResponse createJob(JobOperationRequest request);

    /**
     * 自然语言修改作业
     */
    JobOperationResponse updateJob(JobOperationRequest request);

    /**
     * 自然语言发布作业
     */
    JobOperationResponse publishJob(JobOperationRequest request);

    /**
     * 自然语言删除作业
     */
    JobOperationResponse deleteJob(JobOperationRequest request);
}
