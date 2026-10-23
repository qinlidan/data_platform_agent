package com.dataplatform.ai.tools;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.dto.DataSourceRequest;
import com.dataplatform.entity.DataSource;
import com.dataplatform.enums.DataSourceType;
import com.dataplatform.exception.BusinessException;
import com.dataplatform.service.DataSourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 数据源管理工具类
 * 使用 @Tool 和 @ToolParam 注解，供 AI 调用
 */
@Component("dataSourceTool")
@Slf4j
public class DataSourceTool {

    private final DataSourceService dataSourceService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DataSourceTool(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @Tool(description = """
        查询数据源详情。根据数据源ID获取数据源的完整信息，包括：
        - 基本信息：数据源名称、编码、类型
        - 连接配置：URL、用户名、端口
        - 创建和更新时间

        参数：
        - datasourceId: 数据源ID，必填

        返回：格式化的数据源详情字符串
        """)
    public String getDataSourceDetails(@ToolParam(description = "数据源ID") Long datasourceId) {
        try {
            log.info("[AI Tool] getDataSourceDetails - datasourceId: {}", datasourceId);
            DataSource ds = dataSourceService.getById(datasourceId);
            if (ds == null) {
                return "未找到ID为 " + datasourceId + " 的数据源";
            }

            return String.format("""
                数据源详情：
                - 数据源ID：%d
                - 数据源名称：%s
                - 数据源编码：%s
                - 数据源类型：%s
                - 连接URL：%s
                - 用户名：%s
                - 创建时间：%s
                - 更新时间：%s
                """,
                    ds.getId(), ds.getSourceName(), ds.getSourceCode(), ds.getSourceType(),
                    ds.getJdbcUrl(), ds.getUsername(),
                    ds.getCreateTime().format(DATE_FORMATTER),
                    ds.getUpdateTime().format(DATE_FORMATTER)
            );
        } catch (Exception e) {
            log.error("[AI Tool] getDataSourceDetails 失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        查询所有数据源列表。返回系统中配置的所有数据源。

        返回：数据源列表摘要
        """)
    public String listAllDataSources() {
        try {
            log.info("[AI Tool] listAllDataSources");
            List<DataSource> list = dataSourceService.listAll();

            if (list.isEmpty()) {
                return "当前没有配置数据源";
            }

            StringBuilder sb = new StringBuilder("数据源列表（共 " + list.size() + " 个）：\n");
            for (DataSource ds : list) {
                sb.append(String.format("- [%d] %s (%s) - %s - %s\n",
                        ds.getId(), ds.getSourceName(), ds.getSourceCode(),
                        ds.getSourceType(), ds.getJdbcUrl()));
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[AI Tool] listAllDataSources 失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        搜索数据源。根据关键词搜索数据源（模糊匹配数据源名称或编码）。

        参数：
        - keyword: 搜索关键词（可选）
        - pageNum: 页码（可选，默认1）
        - pageSize: 每页数量（可选，默认10）

        返回：搜索结果摘要
        """)
    public String searchDataSources(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "页码，默认1") Integer pageNum,
            @ToolParam(description = "每页数量，默认10") Integer pageSize
    ) {
        try {
            if (pageNum == null || pageNum <= 0) {
                pageNum = 1;
            }
            if (pageSize == null || pageSize <= 0) {
                pageSize = 10;
            }

            log.info("[AI Tool] searchDataSources - keyword: {}, pageNum: {}, pageSize: {}",
                    keyword, pageNum, pageSize);

            Page<DataSource> page = dataSourceService.page(pageNum, pageSize, keyword);
            List<DataSource> list = page.getRecords();

            if (list.isEmpty()) {
                return "没有找到符合条件的数据源";
            }

            StringBuilder sb = new StringBuilder(
                    String.format("找到 %d 条数据源（共 %d 条，第 %d/%d 页）：\n",
                            list.size(), page.getTotal(), pageNum, page.getPages()));

            for (DataSource ds : list) {
                sb.append(String.format("- [%d] %s (%s) - %s\n",
                        ds.getId(), ds.getSourceName(), ds.getSourceCode(), ds.getSourceType()));
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[AI Tool] searchDataSources 失败", e);
            return "搜索失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        创建新数据源。根据提供的参数创建一个新的数据源。

        参数：
        - sourceName: 数据源名称（必填）
        - sourceCode: 数据源编码（必填，唯一标识）
        - sourceType: 数据源类型（必填：MYSQL、POSTGRESQL、ORACLE、SQLSERVER、DB2、HIVE）
        - jdbcUrl: JDBC连接URL（必填，例如：jdbc:mysql://localhost:3306/test）
        - username: 用户名（必填）
        - password: 密码（必填）
        - description: 描述（可选）

        返回：操作结果描述
        """)
    public String createDataSource(
            @ToolParam(description = "数据源名称") String sourceName,
            @ToolParam(description = "数据源编码") String sourceCode,
            @ToolParam(description = "数据源类型：MYSQL | POSTGRESQL | ORACLE | SQLSERVER | DB2 | HIVE") String sourceType,
            @ToolParam(description = "JDBC连接URL") String jdbcUrl,
            @ToolParam(description = "用户名") String username,
            @ToolParam(description = "密码") String password,
            @ToolParam(description = "描述") String description
    ) {
        try {
            log.info("[AI Tool] createDataSource - sourceName: {}, sourceCode: {}", sourceName, sourceCode);

            DataSourceRequest request = new DataSourceRequest();
            request.setSourceName(sourceName);
            request.setSourceCode(sourceCode);
            request.setSourceType(DataSourceType.valueOf(sourceType));
            request.setJdbcUrl(jdbcUrl);
            request.setUsername(username);
            request.setPassword(password);
            request.setDescription(description);

            DataSource ds = dataSourceService.create(request);
            return String.format("数据源创建成功！数据源ID：%d，名称：%s，编码：%s",
                    ds.getId(), ds.getSourceName(), ds.getSourceCode());
        } catch (BusinessException e) {
            log.error("[AI Tool] createDataSource 业务异常", e);
            return "创建失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[AI Tool] createDataSource 失败", e);
            return "创建失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        修改数据源配置。更新数据源的配置信息。

        参数：
        - datasourceId: 数据源ID（必填）
        - sourceName: 新的数据源名称（可选）
        - jdbcUrl: 新的JDBC连接URL（可选）
        - username: 新的用户名（可选）
        - password: 新的密码（可选）
        - description: 新的描述（可选）

        返回：操作结果描述
        """)
    public String updateDataSource(
            @ToolParam(description = "数据源ID") Long datasourceId,
            @ToolParam(description = "数据源名称") String sourceName,
            @ToolParam(description = "JDBC连接URL") String jdbcUrl,
            @ToolParam(description = "用户名") String username,
            @ToolParam(description = "密码") String password,
            @ToolParam(description = "描述") String description
    ) {
        try {
            log.info("[AI Tool] updateDataSource - datasourceId: {}", datasourceId);

            DataSource ds = dataSourceService.getById(datasourceId);
            if (ds == null) {
                return "修改失败：未找到ID为 " + datasourceId + " 的数据源";
            }

            DataSourceRequest request = new DataSourceRequest();
            request.setSourceName(sourceName);
            request.setSourceCode(ds.getSourceCode()); // 编码不可修改
            request.setSourceType(ds.getSourceType()); // 类型不可修改
            request.setJdbcUrl(jdbcUrl);
            request.setUsername(username);
            request.setPassword(password);
            request.setDescription(description);

            dataSourceService.update(datasourceId, request);
            return String.format("数据源 %s 修改成功！", ds.getSourceName());
        } catch (BusinessException e) {
            log.error("[AI Tool] updateDataSource 业务异常", e);
            return "修改失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[AI Tool] updateDataSource 失败", e);
            return "修改失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        删除数据源。删除指定的数据源（需要用户二次确认）。

        参数：
        - datasourceId: 数据源ID（必填）
        - confirmed: 是否已确认（必填，首次调用传false，用户确认后传true）

        返回：操作结果描述或确认提示
        """)
    public String deleteDataSource(
            @ToolParam(description = "数据源ID") Long datasourceId,
            @ToolParam(description = "是否已确认") Boolean confirmed
    ) {
        try {
            log.info("[AI Tool] deleteDataSource - datasourceId: {}, confirmed: {}", datasourceId, confirmed);

            DataSource ds = dataSourceService.getById(datasourceId);
            if (ds == null) {
                return "删除失败：未找到ID为 " + datasourceId + " 的数据源";
            }

            if (confirmed == null || !confirmed) {
                return String.format("⚠️ 确认删除数据源 %s（%s）吗？此操作不可恢复！请回复\"确认\"继续。",
                        ds.getSourceName(), ds.getSourceCode());
            }

            dataSourceService.delete(datasourceId);
            return String.format("数据源 %s 已删除", ds.getSourceName());
        } catch (BusinessException e) {
            log.error("[AI Tool] deleteDataSource 业务异常", e);
            return "删除失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[AI Tool] deleteDataSource 失败", e);
            return "删除失败：" + e.getMessage();
        }
    }

    @Tool(description = """
        测试数据源连接。测试指定数据源的连接是否正常。

        参数：
        - datasourceId: 数据源ID（必填）

        返回：测试结果描述
        """)
    public String testDataSourceConnection(@ToolParam(description = "数据源ID") Long datasourceId) {
        try {
            log.info("[AI Tool] testDataSourceConnection - datasourceId: {}", datasourceId);

            DataSource ds = dataSourceService.getById(datasourceId);
            if (ds == null) {
                return "测试失败：未找到ID为 " + datasourceId + " 的数据源";
            }

            boolean success = dataSourceService.testConnection(datasourceId);
            if (success) {
                return String.format("✅ 数据源 %s 连接测试成功！", ds.getSourceName());
            } else {
                return String.format("❌ 数据源 %s 连接测试失败！", ds.getSourceName());
            }
        } catch (Exception e) {
            log.error("[AI Tool] testDataSourceConnection 失败", e);
            return "测试失败：" + e.getMessage();
        }
    }
}
