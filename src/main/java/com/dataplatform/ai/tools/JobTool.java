package com.dataplatform.ai.tools;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.ai.confirmation.RequireConfirmation;
import com.dataplatform.dto.JobCreateRequest;
import com.dataplatform.dto.JobQueryRequest;
import com.dataplatform.dto.JobUpdateRequest;
import com.dataplatform.entity.DataSource;
import com.dataplatform.entity.Job;
import com.dataplatform.enums.EngineType;
import com.dataplatform.enums.JobStatus;
import com.dataplatform.enums.JobType;
import com.dataplatform.exception.BusinessException;
import com.dataplatform.service.DataSourceService;
import com.dataplatform.service.JobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 作业管理工具类
 * 使用 @Tool 和 @ToolParam 注解，供 AI 调用
 * 支持流式推送表格事件到前端
 */
@Component("jobTool")
@Slf4j
public class JobTool {

    private final JobService jobService;
    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 每个请求的独立 Sink（通过 Controller 注入）
    private Sinks.Many<Map<String, Object>> uiEventSink;

    public JobTool(JobService jobService, DataSourceService dataSourceService) {
        this.jobService = jobService;
        this.dataSourceService = dataSourceService;
    }

    // 设置当前请求的 Sink
    public void setUiEventSink(Sinks.Many<Map<String, Object>> sink) {
        this.uiEventSink = sink;
    }

    // 发射 UI 事件（表格行/状态）
    private void emit(String event, Object payload) {
        if (uiEventSink != null) {
            Sinks.EmitResult result = uiEventSink.tryEmitNext(Map.of("event", event, "payload", payload));
            if (result.isFailure()) {
                log.warn("UI事件发射失败: event={}, result=", event, result);
            }
        }
    }

    @Tool(description = """
        查询作业详情。根据作业ID获取作业的完整信息，包括：
        - 基本信息：作业名称、中文名称、类型、状态
        - 数据源配置：源库表、目标库表、执行引擎
        - 版本信息：当前版本、发布版本
        - 创建和更新时间

        参数：
        - jobId: 作业ID，必填

        返回：格式化的作业详情字符串
        说明：本工具getJobDetails无法直接返回数据源详情，只能拿到数据源id,SourceDatasourceId之后，
        调用数据源查询工具才能获取数据源详情
        """)
    public String getJobDetails(@ToolParam(description = "作业ID") Long jobId) {
        try {
            log.info("[AI Tool] getJobDetails - jobId: {}", jobId);
            Job job = jobService.getById(jobId);

            return String.format("""
                作业详情：
                - 作业ID：%d
                - 作业名称：%s
                - 作业中文名称：%s
                - 作业类型：%s
                - 状态：%s
                - 源数据源ID：%d
                - 源库表：%s.%s
                - 目标库表：%s.%s
                - 执行引擎：%s
                - 当前版本：%d
                - 创建时间：%s
                - 更新时间：%s
                """,
                    job.getId(), job.getJobName(), job.getJobCnName(), job.getJobType(), job.getStatus(),
                    job.getSourceDatasourceId(), job.getSourceDatabase(), job.getSourceTable(),
                    job.getTargetDatabase(), job.getTargetTable(), job.getEngineType(),
                    job.getCurrentVersion(),
                    job.getCreateTime().format(DATE_FORMATTER),
                    job.getUpdateTime().format(DATE_FORMATTER)
            );
        } catch (Exception e) {
            log.error("[AI Tool] getJobDetails 失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        查询最近的作业列表。返回最近创建的作业，按创建时间倒序，以表格形式流式推送到前端。

        参数：
        - limit: 返回的作业数量，默认10条

        返回：摘要文字（表格数据通过SSE事件流推送）
        """)
    public String listRecentJobs(@ToolParam(description = "返回数量，默认10") Integer limit) {
        try {
            if (limit == null || limit <= 0) {
                limit = 10;
            }
            log.info("[AI Tool] listRecentJobs - limit: {}", limit);

            JobQueryRequest request = new JobQueryRequest();
            request.setPageNum(1);
            request.setPageSize(limit);

            Page<Job> page = jobService.page(request);
            List<Job> jobs = page.getRecords();

            if (jobs.isEmpty()) {
                return "当前没有作业记录";
            }

            // 发射表格事件（流式推送）
            emit("table_start", Map.of("type", "job_list", "total", jobs.size()));

            for (Job job : jobs) {
                Map<String, Object> row = Map.of(
                        "id", job.getId(),
                        "jobName", job.getJobName(),
                        "jobCnName", job.getJobCnName(),
                        "jobType", job.getJobType().toString(),
                        "status", job.getStatus().toString(),
                        "sourceTable", job.getSourceDatabase() + "." + job.getSourceTable(),
                        "targetTable", job.getTargetDatabase() + "." + job.getTargetTable(),
                        "createTime", job.getCreateTime().format(DATE_FORMATTER)
                );
                emit("table_row", Map.of("type", "job_list", "row", row));
            }

            emit("table_end", Map.of());

            // 构造作业ID列表，供大模型后续指代消解
            String jobIdList = jobs.stream()
                    .map(job -> String.format("ID=%d(%s)", job.getId(), job.getJobCnName()))
                    .collect(Collectors.joining(", "));

            return String.format("查询到 %d 条作业记录：%s", jobs.size(), jobIdList);
        } catch (Exception e) {
            log.error("[AI Tool] listRecentJobs 失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        搜索作业。根据作业类型、作业状态搜索作业，以表格形式流式推送到前端。

        参数：
        - jobType: 作业类型（DB2HIVE 或 HIVE2HIVE），可选
        - status: 作业状态（DRAFT、PUBLISHED、ARCHIVED），可选

        返回：摘要文字（表格数据通过SSE事件流推送）
        """)
    public String searchJobs(
            @ToolParam(description = "作业类型：DB2HIVE | HIVE2HIVE") String jobType,
            @ToolParam(description = "作业状态：DRAFT | PUBLISHED | ARCHIVED") String status
    ) {
        try {
            log.info("[AI Tool] searchJobs - jobType: {}, status: {}", jobType, status);

            JobQueryRequest request = new JobQueryRequest();
            request.setPageNum(1);
            request.setPageSize(20);

            // 枚举转换
            if (jobType != null && !jobType.isBlank()) {
                request.setJobType(JobType.valueOf(jobType));
            }
            if (status != null && !status.isBlank()) {
                request.setStatus(JobStatus.valueOf(status));
            }

            Page<Job> page = jobService.page(request);
            List<Job> jobs = page.getRecords();

            if (jobs.isEmpty()) {
                return "没有找到符合条件的作业";
            }

            // 发射表格事件（流式推送）
            emit("table_start", Map.of("type", "job_list", "total", jobs.size()));

            for (Job job : jobs) {
                Map<String, Object> row = Map.of(
                        "id", job.getId(),
                        "jobName", job.getJobName(),
                        "jobCnName", job.getJobCnName(),
                        "jobType", job.getJobType().toString(),
                        "status", job.getStatus().toString(),
                        "sourceTable", job.getSourceDatabase() + "." + job.getSourceTable(),
                        "targetTable", job.getTargetDatabase() + "." + job.getTargetTable(),
                        "createTime", job.getCreateTime().format(DATE_FORMATTER)
                );
                emit("table_row", Map.of("type", "job_list", "row", row));
            }

            emit("table_end", Map.of());

            // 构造作业ID列表，供大模型后续指代消解
            String jobIdList = jobs.stream()
                    .map(job -> String.format("ID=%d(%s)", job.getId(), job.getJobCnName()))
                    .collect(Collectors.joining(", "));

            return String.format("找到 %d 条符合条件的作业：%s", jobs.size(), jobIdList);
        } catch (Exception e) {
            log.error("[AI Tool] searchJobs 失败", e);
            return "搜索失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        创建新作业。根据提供的参数创建一个新的ETL作业。
        返回：操作结果描述
        """)
    public String createJob(
            @Valid @ToolParam(description = "作业创建请求") JobCreateRequest request,
            ToolContext toolContext) {
        try {
            log.info("[AI Tool] createJob - jobName: {}, jobCnName: {}",
                    request.getJobName(), request.getJobCnName());

            // 从 ToolContext 获取当前用户ID
            String creatorUserId = (String) toolContext.getContext().get("userId");
            if (creatorUserId == null) {
                return "创建失败：无法获取当前用户信息";
            }
            log.info("[AI Tool] createJob - 创建者: {}", creatorUserId);

            Job job = jobService.create(request, creatorUserId);
            return String.format("作业创建成功！作业ID：%d，作业名称：%s，中文名称：%s",
                    job.getId(), job.getJobName(), job.getJobCnName());
        } catch (BusinessException e) {
            log.error("[AI Tool] createJob 业务异常", e);
            return "创建失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[AI Tool] createJob 失败", e);
            return "创建失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        修改作业配置。更新作业的配置信息（仅限草稿状态的作业）。

        参数：
        - jobId: 作业ID（必填）
        - updateRequest: 作业更新请求对象，包含：
          - jobCnName: 新的作业中文名称（可选）
          - sourceDatabase: 新的源数据库名（可选）
          - sourceTable: 新的源表名（可选）
          - targetDatabase: 新的目标数据库名（可选）
          - targetTable: 新的目标表名（可选）
          - description: 新的作业描述（可选）

        返回：操作结果描述
        """)
    public String updateJobConfig(
            @ToolParam(description = "作业ID") Long jobId,
            @ToolParam(description = "作业更新请求") JobUpdateRequest updateRequest,
            ToolContext toolContext
    ) {
        try {
            log.info("[AI Tool] updateJobConfig - jobId: {}", jobId);

            // 从 ToolContext 获取当前用户ID
            String operatorUserId = (String) toolContext.getContext().get("userId");
            if (operatorUserId == null) {
                return "修改失败：无法获取当前用户信息";
            }

            Job job = jobService.getById(jobId);
            jobService.update(job.getId(), updateRequest, operatorUserId);
            return String.format("作业 ID=%d 修改成功！", jobId);
        } catch (BusinessException e) {
            log.error("[AI Tool] updateJobConfig 业务异常", e);
            return "修改失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[AI Tool] updateJobConfig 失败", e);
            return "修改失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        删除作业。删除指定的作业（需要用户二次确认）。

        参数：
        - jobId: 作业ID（必填）
        - confirmed: 是否已确认（必填，首次调用传false，用户确认后传true）

        返回：操作结果描述或确认提示
        """)
    @RequireConfirmation(operation = "删除作业", riskLevel = "high")
    public String deleteJob(
            @ToolParam(description = "jobId") Long jobId,
            @ToolParam(description = "confirmed") Boolean confirmed,
            ToolContext toolContext
    ) {
        try {
            log.info("[AI Tool] deleteJob - jobId: {}, confirmed: {}", jobId, confirmed);

            Job job = jobService.getById(jobId);

            if (confirmed == null || !confirmed) {
                return String.format("⚠️ 确认删除作业 ID=%d（%s/%s）吗？此操作不可恢复！请\"确认\"继续。",
                        job.getId(), job.getJobName(), job.getJobCnName());
            }

            // 从 ToolContext 获取当前用户ID
            String operatorUserId = (String) toolContext.getContext().get("userId");
            if (operatorUserId == null) {
                return "删除失败：无法获取当前用户信息";
            }

            jobService.delete(job.getId(), operatorUserId);
            return String.format("作业 ID=%d 已删除", jobId);
        } catch (BusinessException e) {
            log.error("[AI Tool] deleteJob 业务异常", e);
            return "删除失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[AI Tool] deleteJob 失败", e);
            return "删除失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        发布作业。将作业的指定版本发布到生产环境（需要用户二次确认）。

        参数：
        - jobId: 作业ID（必填）
        - version: 要发布的版本号（必填）
        - confirmed: 是否已确认（必填，首次调用传false，用户确认后传true）

        返回：操作结果描述或确认提示
        """)
    @RequireConfirmation(operation = "发布作业", riskLevel = "medium")
    public String publishJob(
            @ToolParam(description = "jobId") Long jobId,
            @ToolParam(description = "version") Integer version,
            @ToolParam(description = "confirmed") Boolean confirmed,
            ToolContext toolContext
    ) {
        try {
            log.info("[AI Tool] publishJob - jobId: {}, version: {}, confirmed: {}", jobId, version, confirmed);

            Job job = jobService.getById(jobId);

            if (confirmed == null || !confirmed) {
                return String.format("⚠️ 确认发布作业 ID=%d（%s/%s）的版本 %d 吗？发布后将影响生产环境！请\"确认\"继续。",
                        job.getId(), job.getJobName(), job.getJobCnName(), version);
            }

            // 从 ToolContext 获取当前用户ID
            String operatorUserId = (String) toolContext.getContext().get("userId");
            if (operatorUserId == null) {
                return "发布失败：无法获取当前用户信息";
            }

            jobService.publish(job.getId(), version, operatorUserId);
            return String.format("作业 ID=%d 的版本 %d 已发布！", jobId, version);
        } catch (BusinessException e) {
            log.error("[AI Tool] publishJob 业务异常", e);
            return "发布失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[AI Tool] publishJob 失败", e);
            return "发布失败：" + e.getMessage();
        }
    }
}
