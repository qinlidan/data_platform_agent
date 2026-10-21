package com.dataplatform.ai.tools;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.dto.JobCreateRequest;
import com.dataplatform.dto.JobQueryRequest;
import com.dataplatform.dto.JobUpdateRequest;
import com.dataplatform.entity.DataSource;
import com.dataplatform.entity.Job;
import com.dataplatform.enums.EngineType;
import com.dataplatform.enums.JobType;
import com.dataplatform.exception.BusinessException;
import com.dataplatform.service.DataSourceService;
import com.dataplatform.service.JobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
        查询作业详情。根据作业编码（jobCode）获取作业的完整信息，包括：
        - 基本信息：作业编码、名称、类型、状态
        - 数据源配置：源库表、目标库表、执行引擎
        - 版本信息：当前版本、发布版本
        - 创建和更新时间

        参数：
        - jobCode: 作业编码，必填

        返回：格式化的作业详情字符串
        """)
    public String getJobDetails(@ToolParam(description = "作业编码，例如 job_001") String jobCode) {
        try {
            log.info("[AI Tool] getJobDetails - jobCode: {}", jobCode);
            Job job = jobService.getByCode(jobCode);
            if (job == null) {
                return "未找到作业编码为 " + jobCode + " 的作业";
            }

            return String.format("""
                作业详情：
                - 作业编码：%s
                - 作业名称：%s
                - 作业类型：%s
                - 状态：%s
                - 源数据源ID：%d
                - 源库表：%s.%s
                - 目标库表：%s.%s
                - 执行引擎：%s
                - 当前版本：%d
                - 发布版本：%d
                - 创建时间：%s
                - 更新时间：%s
                """,
                    job.getJobCode(), job.getJobName(), job.getJobType(), job.getStatus(),
                    job.getSourceDatasourceId(), job.getSourceDatabase(), job.getSourceTable(),
                    job.getTargetDatabase(), job.getTargetTable(), job.getEngineType(),
                    job.getCurrentVersion(), job.getPublishedVersion(),
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
                        "jobCode", job.getJobCode(),
                        "jobName", job.getJobName(),
                        "jobType", job.getJobType().toString(),
                        "status", job.getStatus().toString(),
                        "sourceTable", job.getSourceDatabase() + "." + job.getSourceTable(),
                        "targetTable", job.getTargetDatabase() + "." + job.getTargetTable(),
                        "createTime", job.getCreateTime().format(DATE_FORMATTER)
                );
                emit("table_row", Map.of("type", "job_list", "row", row));
            }

            emit("table_end", Map.of());

            return String.format("查询到 %d 条作业记录", jobs.size());
        } catch (Exception e) {
            log.error("[AI Tool] listRecentJobs 失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        搜索作业。根据关键词、作业类型、作业状态搜索作业，以表格形式流式推送到前端。

        参数：
        - keyword: 搜索关键词（模糊匹配作业编码或名称），可选
        - jobType: 作业类型（DB2HIVE 或 HIVE2HIVE），可选
        - status: 作业状态（DRAFT、PUBLISHED、ARCHIVED），可选

        返回：摘要文字（表格数据通过SSE事件流推送）
        """)
    public String searchJobs(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "作业类型：DB2HIVE | HIVE2HIVE") String jobType,
            @ToolParam(description = "作业状态：DRAFT | PUBLISHED | ARCHIVED") String status
    ) {
        try {
            log.info("[AI Tool] searchJobs - keyword: {}, jobType: {}, status: {}", keyword, jobType, status);

            JobQueryRequest request = new JobQueryRequest();
            request.setPageNum(1);
            request.setPageSize(20);
            request.setKeyword(keyword);
            request.setJobType(jobType);
            request.setStatus(status);

            Page<Job> page = jobService.page(request);
            List<Job> jobs = page.getRecords();

            if (jobs.isEmpty()) {
                return "没有找到符合条件的作业";
            }

            // 发射表格事件（流式推送）
            emit("table_start", Map.of("type", "job_list", "total", jobs.size()));

            for (Job job : jobs) {
                Map<String, Object> row = Map.of(
                        "jobCode", job.getJobCode(),
                        "jobName", job.getJobName(),
                        "jobType", job.getJobType().toString(),
                        "status", job.getStatus().toString(),
                        "sourceTable", job.getSourceDatabase() + "." + job.getSourceTable(),
                        "targetTable", job.getTargetDatabase() + "." + job.getTargetTable(),
                        "createTime", job.getCreateTime().format(DATE_FORMATTER)
                );
                emit("table_row", Map.of("type", "job_list", "row", row));
            }

            emit("table_end", Map.of());

            return String.format("找到 %d 条符合条件的作业", jobs.size());
        } catch (Exception e) {
            log.error("[AI Tool] searchJobs 失败", e);
            return "搜索失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        创建新作业。根据提供的参数创建一个新的ETL作业。

        参数：
        - jobCode: 作业编码（必填）
        - jobName: 作业名称（必填）
        - jobType: 作业类型，DB2HIVE 或 HIVE2HIVE（必填）
        - sourceDatasourceCode: 源数据源编码（必填，例如 DB2_PROD）
        - sourceDatabase: 源数据库名（必填）
        - sourceTable: 源表名（必填）
        - targetDatabase: 目标数据库名（必填）
        - targetTable: 目标表名（必填）
        - engineType: 执行引擎，SPARK 或 HIVE（必填）
        - description: 作业描述（可选）

        返回：操作结果描述
        """)
    public String createJob(
            @ToolParam(description = "jobCode") String jobCode,
            @ToolParam(description = "jobName") String jobName,
            @ToolParam(description = "jobType") String jobType,
            @ToolParam(description = "sourceDatasourceCode") String sourceDatasourceCode,
            @ToolParam(description = "sourceDatabase") String sourceDatabase,
            @ToolParam(description = "sourceTable") String sourceTable,
            @ToolParam(description = "targetDatabase") String targetDatabase,
            @ToolParam(description = "targetTable") String targetTable,
            @ToolParam(description = "engineType") String engineType,
            @ToolParam(description = "description") String description
    ) {
        try {
            log.info("[AI Tool] createJob - jobCode: {}, jobName: {}", jobCode, jobName);

            // 通过数据源编码查找数据源ID
            DataSource dataSource = dataSourceService.getByCode(sourceDatasourceCode);
            if (dataSource == null) {
                return "创建失败：数据源编码 " + sourceDatasourceCode + " 不存在";
            }

            JobCreateRequest request = new JobCreateRequest();
            request.setJobCode(jobCode);
            request.setJobName(jobName);
            request.setJobType(JobType.valueOf(jobType));
            request.setSourceDatasourceId(dataSource.getId());
            request.setSourceDatabase(sourceDatabase);
            request.setSourceTable(sourceTable);
            request.setTargetDatabase(targetDatabase);
            request.setTargetTable(targetTable);
            request.setEngineType(EngineType.valueOf(engineType));
            request.setDescription(description);

            Job job = jobService.create(request);
            return String.format("作业创建成功！作业编码：%s，作业名称：%s", job.getJobCode(), job.getJobName());
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
        - jobCode: 作业编码（必填）
        - jobName: 新的作业名称（可选）
        - sourceDatabase: 新的源数据库名（可选）
        - sourceTable: 新的源表名（可选）
        - targetDatabase: 新的目标数据库名（可选）
        - targetTable: 新的目标表名（可选）
        - description: 新的作业描述（可选）

        返回：操作结果描述
        """)
    public String updateJobConfig(
            @ToolParam(description = "jobCode") String jobCode,
            @ToolParam(description = "jobName") String jobName,
            @ToolParam(description = "sourceDatabase") String sourceDatabase,
            @ToolParam(description = "sourceTable") String sourceTable,
            @ToolParam(description = "targetDatabase") String targetDatabase,
            @ToolParam(description = "targetTable") String targetTable,
            @ToolParam(description = "description") String description
    ) {
        try {
            log.info("[AI Tool] updateJobConfig - jobCode: {}", jobCode);

            Job job = jobService.getByCode(jobCode);
            if (job == null) {
                return "修改失败：未找到作业编码为 " + jobCode + " 的作业";
            }

            JobUpdateRequest request = new JobUpdateRequest();
            request.setJobName(jobName);
            request.setSourceDatabase(sourceDatabase);
            request.setSourceTable(sourceTable);
            request.setTargetDatabase(targetDatabase);
            request.setTargetTable(targetTable);
            request.setDescription(description);

            jobService.update(job.getId(), request);
            return String.format("作业 %s 修改成功！", jobCode);
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
        - jobCode: 作业编码（必填）
        - confirmed: 是否已确认（必填，首次调用传false，用户确认后传true）

        返回：操作结果描述或确认提示
        """)
    public String deleteJob(
            @ToolParam(description = "jobCode") String jobCode,
            @ToolParam(description = "confirmed") Boolean confirmed
    ) {
        try {
            log.info("[AI Tool] deleteJob - jobCode: {}, confirmed: {}", jobCode, confirmed);

            Job job = jobService.getByCode(jobCode);
            if (job == null) {
                return "删除失败：未找到作业编码为 " + jobCode + " 的作业";
            }

            if (confirmed == null || !confirmed) {
                return String.format("⚠️ 确认删除作业 %s（%s）吗？此操作不可恢复！请回复\"确认\"继续。",
                        job.getJobCode(), job.getJobName());
            }

            jobService.delete(job.getId());
            return String.format("作业 %s 已删除", jobCode);
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
        - jobCode: 作业编码（必填）
        - version: 要发布的版本号（必填）
        - confirmed: 是否已确认（必填，首次调用传false，用户确认后传true）

        返回：操作结果描述或确认提示
        """)
    public String publishJob(
            @ToolParam(description = "jobCode") String jobCode,
            @ToolParam(description = "version") Integer version,
            @ToolParam(description = "confirmed") Boolean confirmed
    ) {
        try {
            log.info("[AI Tool] publishJob - jobCode: {}, version: {}, confirmed: {}", jobCode, version, confirmed);

            Job job = jobService.getByCode(jobCode);
            if (job == null) {
                return "发布失败：未找到作业编码为 " + jobCode + " 的作业";
            }

            if (confirmed == null || !confirmed) {
                return String.format("⚠️ 确认发布作业 %s 的版本 %d 吗？发布后将影响生产环境！请回复\"确认\"继续。",
                        job.getJobCode(), version);
            }

            jobService.publish(job.getId(), version);
            return String.format("作业 %s 的版本 %d 已发布！", jobCode, version);
        } catch (BusinessException e) {
            log.error("[AI Tool] publishJob 业务异常", e);
            return "发布失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[AI Tool] publishJob 失败", e);
            return "发布失败：" + e.getMessage();
        }
    }
}
