package com.dataplatform.config;

import com.dataplatform.ai.validation.ToolValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 工具 Schema 注册器
 * <p>
 * 在应用启动时注册所有工具的参数定义（schema）
 * 用于工具调用校验
 *
 * @author Data Platform Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolSchemaRegistry implements ApplicationRunner {

    private final ToolValidator toolValidator;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[ToolSchemaRegistry] 开始注册工具 Schema...");

        // 1. DataSourceTool Schema
        registerDataSourceToolSchema();

        // 2. JobTool Schema
        registerJobToolSchema();

        // 3. FaqTool Schema
        registerFaqToolSchema();

        log.info("[ToolSchemaRegistry] 工具 Schema 注册完成，已注册 {} 个工具",
                toolValidator.getRegisteredTools().size());
    }

    /**
     * 注册 DataSourceTool 的 Schema
     */
    private void registerDataSourceToolSchema() {
        // queryDataSources - 查询数据源列表
        ToolValidator.ToolSchema queryDataSourcesSchema = new ToolValidator.ToolSchema()
                .addParam("status", "String", "数据源状态（ACTIVE, INACTIVE）", false)
                .addParam("type", "String", "数据源类型（MYSQL, POSTGRESQL, ORACLE 等）", false);
        toolValidator.registerTool("queryDataSources", queryDataSourcesSchema);

        // getDataSourceById - 根据 ID 查询数据源
        ToolValidator.ToolSchema getDataSourceByIdSchema = new ToolValidator.ToolSchema()
                .addParam("datasourceId", "Integer", "数据源 ID", true);
        toolValidator.registerTool("getDataSourceById", getDataSourceByIdSchema);

        // createDataSource - 创建数据源
        ToolValidator.ToolSchema createDataSourceSchema = new ToolValidator.ToolSchema()
                .addParam("datasourceName", "String", "数据源名称", true)
                .addParam("datasourceCode", "String", "数据源编码（唯一）", true)
                .addParam("datasourceType", "String", "数据源类型（MYSQL, POSTGRESQL 等）", true)
                .addParam("host", "String", "主机地址", true)
                .addParam("port", "Integer", "端口号", true)
                .addParam("database", "String", "数据库名", true)
                .addParam("username", "String", "用户名", true)
                .addParam("password", "String", "密码", true)
                .addParam("description", "String", "描述", false);
        toolValidator.registerTool("createDataSource", createDataSourceSchema);

        // updateDataSource - 更新数据源
        ToolValidator.ToolSchema updateDataSourceSchema = new ToolValidator.ToolSchema()
                .addParam("datasourceId", "Integer", "数据源 ID", true)
                .addParam("datasourceName", "String", "数据源名称", false)
                .addParam("host", "String", "主机地址", false)
                .addParam("port", "Integer", "端口号", false)
                .addParam("database", "String", "数据库名", false)
                .addParam("username", "String", "用户名", false)
                .addParam("password", "String", "密码", false)
                .addParam("description", "String", "描述", false);
        toolValidator.registerTool("updateDataSource", updateDataSourceSchema);

        // deleteDataSource - 删除数据源
        ToolValidator.ToolSchema deleteDataSourceSchema = new ToolValidator.ToolSchema()
                .addParam("datasourceId", "Integer", "数据源 ID", true);
        toolValidator.registerTool("deleteDataSource", deleteDataSourceSchema);
    }

    /**
     * 注册 JobTool 的 Schema
     */
    private void registerJobToolSchema() {
        // queryJobs - 查询作业列表
        ToolValidator.ToolSchema queryJobsSchema = new ToolValidator.ToolSchema()
                .addParam("jobType", "String", "作业类型（DB2HIVE, HIVE2HIVE）", false)
                .addParam("status", "String", "作业状态（DRAFT, PUBLISHED, RUNNING, FAILED）", false);
        toolValidator.registerTool("queryJobs", queryJobsSchema);

        // getJobByCode - 根据作业编码查询
        ToolValidator.ToolSchema getJobByCodeSchema = new ToolValidator.ToolSchema()
                .addParam("jobCode", "String", "作业编码", true);
        toolValidator.registerTool("getJobByCode", getJobByCodeSchema);

        // createJob - 创建作业
        ToolValidator.ToolSchema createJobSchema = new ToolValidator.ToolSchema()
                .addParam("jobCode", "String", "作业编码（唯一）", true)
                .addParam("jobName", "String", "作业名称", true)
                .addParam("jobType", "String", "作业类型（DB2HIVE, HIVE2HIVE）", true)
                .addParam("sourceDatasourceCode", "String", "源数据源编码", true)
                .addParam("sourceDatabase", "String", "源数据库名", true)
                .addParam("sourceTable", "String", "源表名", true)
                .addParam("targetDatabase", "String", "目标数据库名", true)
                .addParam("targetTable", "String", "目标表名", true)
                .addParam("engineType", "String", "执行引擎（SPARK, HIVE）", true)
                .addParam("description", "String", "作业描述", false)
                .addParam("scheduleType", "String", "调度类型（CRON, MANUAL）", false)
                .addParam("cronExpression", "String", "Cron 表达式", false);
        toolValidator.registerTool("createJob", createJobSchema);

        // updateJob - 更新作业
        ToolValidator.ToolSchema updateJobSchema = new ToolValidator.ToolSchema()
                .addParam("jobCode", "String", "作业编码", true)
                .addParam("jobName", "String", "作业名称", false)
                .addParam("description", "String", "作业描述", false)
                .addParam("scheduleType", "String", "调度类型", false)
                .addParam("cronExpression", "String", "Cron 表达式", false);
        toolValidator.registerTool("updateJob", updateJobSchema);

        // deleteJob - 删除作业
        ToolValidator.ToolSchema deleteJobSchema = new ToolValidator.ToolSchema()
                .addParam("jobCode", "String", "作业编码", true);
        toolValidator.registerTool("deleteJob", deleteJobSchema);

        // publishJob - 发布作业
        ToolValidator.ToolSchema publishJobSchema = new ToolValidator.ToolSchema()
                .addParam("jobCode", "String", "作业编码", true);
        toolValidator.registerTool("publishJob", publishJobSchema);

        // executeJob - 立即执行作业
        ToolValidator.ToolSchema executeJobSchema = new ToolValidator.ToolSchema()
                .addParam("jobCode", "String", "作业编码", true);
        toolValidator.registerTool("executeJob", executeJobSchema);
    }

    /**
     * 注册 FaqTool 的 Schema
     */
    private void registerFaqToolSchema() {
        // searchFaq - 搜索 FAQ
        ToolValidator.ToolSchema searchFaqSchema = new ToolValidator.ToolSchema()
                .addParam("keyword", "String", "搜索关键词", true)
                .addParam("category", "String", "FAQ 分类", false);
        toolValidator.registerTool("searchFaq", searchFaqSchema);

        // getFaqById - 根据 ID 查询 FAQ
        ToolValidator.ToolSchema getFaqByIdSchema = new ToolValidator.ToolSchema()
                .addParam("faqId", "Integer", "FAQ ID", true);
        toolValidator.registerTool("getFaqById", getFaqByIdSchema);
    }
}
