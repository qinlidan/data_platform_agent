package com.dataplatform.ai.tools;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 作业管理工具集
 *
 * 包含：
 * - searchJobs: 搜索作业
 * - getJobDetail: 获取作业详情
 * - createJob: 创建作业
 * - updateJob: 更新作业
 * - deleteJob: 删除作业（需要审批）
 * - publishJob: 发布作业（需要审批）
 */
@Slf4j
@Component
public class JobTools {

    /**
     * 搜索作业
     */
    public Function<SearchJobsRequest, SearchJobsResponse> searchJobs() {
        return request -> {
            log.info("[JobTools] 搜索作业：keyword={}", request.keyword);

            // 模拟查询
            List<JobInfo> jobs = List.of(
                    new JobInfo("job_001", "每日数据同步", "RUNNING"),
                    new JobInfo("job_002", "小时级汇总", "STOPPED"),
                    new JobInfo("job_003", "实时清洗", "RUNNING")
            );

            return new SearchJobsResponse(
                    jobs.stream()
                            .filter(j -> request.keyword == null ||
                                    j.jobName.contains(request.keyword))
                            .toList()
            );
        };
    }

    /**
     * 获取作业详情
     */
    public Function<GetJobDetailRequest, JobDetail> getJobDetail() {
        return request -> {
            log.info("[JobTools] 获取作业详情：jobCode={}", request.jobCode);

            // 模拟查询
            return new JobDetail(
                    request.jobCode,
                    "每日数据同步",
                    "RUNNING",
                    "0 2 * * *",
                    "mysql_source",
                    "hive_target"
            );
        };
    }

    /**
     * 创建作业
     */
    public Function<CreateJobRequest, CreateJobResponse> createJob() {
        return request -> {
            log.info("[JobTools] 创建作业：jobName={}, cron={}", request.jobName, request.cron);

            // 模拟创建
            String jobCode = "job_" + System.currentTimeMillis();

            return new CreateJobResponse(jobCode, "创建成功");
        };
    }

    /**
     * 更新作业
     */
    public Function<UpdateJobRequest, UpdateJobResponse> updateJob() {
        return request -> {
            log.info("[JobTools] 更新作业：jobCode={}, updates={}", request.jobCode, request.updates);

            // 模拟更新
            return new UpdateJobResponse(request.jobCode, "更新成功");
        };
    }

    /**
     * 删除作业（需要审批）
     */
    public Function<DeleteJobRequest, DeleteJobResponse> deleteJob() {
        return request -> {
            log.info("[JobTools] 删除作业：jobCode={}", request.jobCode);

            // 模拟删除
            return new DeleteJobResponse(request.jobCode, "删除成功");
        };
    }

    /**
     * 发布作业（需要审批）
     */
    public Function<PublishJobRequest, PublishJobResponse> publishJob() {
        return request -> {
            log.info("[JobTools] 发布作业：jobCode={}", request.jobCode);

            // 模拟发布
            return new PublishJobResponse(request.jobCode, "发布成功");
        };
    }

    // ========== 请求/响应对象 ==========

    @Data
    public static class SearchJobsRequest {
        @JsonPropertyDescription("搜索关键词（可选）")
        private String keyword;
    }

    @Data
    public static class SearchJobsResponse {
        private List<JobInfo> jobs;

        public SearchJobsResponse(List<JobInfo> jobs) {
            this.jobs = jobs;
        }
    }

    @Data
    public static class JobInfo {
        private String jobCode;
        private String jobName;
        private String status;

        public JobInfo(String jobCode, String jobName, String status) {
            this.jobCode = jobCode;
            this.jobName = jobName;
            this.status = status;
        }
    }

    @Data
    public static class GetJobDetailRequest {
        @JsonPropertyDescription("作业编码")
        private String jobCode;
    }

    @Data
    public static class JobDetail {
        private String jobCode;
        private String jobName;
        private String status;
        private String cron;
        private String sourceId;
        private String targetId;

        public JobDetail(String jobCode, String jobName, String status, String cron, String sourceId, String targetId) {
            this.jobCode = jobCode;
            this.jobName = jobName;
            this.status = status;
            this.cron = cron;
            this.sourceId = sourceId;
            this.targetId = targetId;
        }
    }

    @Data
    public static class CreateJobRequest {
        @JsonPropertyDescription("作业名称")
        private String jobName;

        @JsonPropertyDescription("Cron 表达式")
        private String cron;

        @JsonPropertyDescription("源数据源ID")
        private String sourceId;

        @JsonPropertyDescription("目标数据源ID")
        private String targetId;
    }

    @Data
    public static class CreateJobResponse {
        private String jobCode;
        private String message;

        public CreateJobResponse(String jobCode, String message) {
            this.jobCode = jobCode;
            this.message = message;
        }
    }

    @Data
    public static class UpdateJobRequest {
        @JsonPropertyDescription("作业编码")
        private String jobCode;

        @JsonPropertyDescription("更新内容（JSON格式）")
        private Map<String, Object> updates;
    }

    @Data
    public static class UpdateJobResponse {
        private String jobCode;
        private String message;

        public UpdateJobResponse(String jobCode, String message) {
            this.jobCode = jobCode;
            this.message = message;
        }
    }

    @Data
    public static class DeleteJobRequest {
        @JsonPropertyDescription("作业编码")
        private String jobCode;
    }

    @Data
    public static class DeleteJobResponse {
        private String jobCode;
        private String message;

        public DeleteJobResponse(String jobCode, String message) {
            this.jobCode = jobCode;
            this.message = message;
        }
    }

    @Data
    public static class PublishJobRequest {
        @JsonPropertyDescription("作业编码")
        private String jobCode;
    }

    @Data
    public static class PublishJobResponse {
        private String jobCode;
        private String message;

        public PublishJobResponse(String jobCode, String message) {
            this.jobCode = jobCode;
            this.message = message;
        }
    }
}
