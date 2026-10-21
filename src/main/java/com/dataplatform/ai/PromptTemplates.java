package com.dataplatform.ai;

/**
 * AI 提示词模板
 * 存储各种场景下的提示词模板
 */
public class PromptTemplates {

    /**
     * 作业创建提示词模板
     */
    public static final String JOB_CREATION_TEMPLATE =
        "你是一个数据平台的智能助手。用户想要创建一个数据作业。\n" +
        "\n" +
        "用户需求：{user_input}\n" +
        "\n" +
        "可用数据源列表：\n" +
        "{datasources}\n" +
        "\n" +
        "请分析用户需求，提取以下信息并以 JSON 格式返回：\n" +
        "{\n" +
        "  \"jobName\": \"作业名称\",\n" +
        "  \"jobType\": \"作业类型(DB2HIVE/HIVE2HIVE/SPARK_SQL等)\",\n" +
        "  \"sourceDatasourceId\": \"源数据源ID\",\n" +
        "  \"sourceDatabase\": \"源数据库\",\n" +
        "  \"sourceTable\": \"源表名\",\n" +
        "  \"targetDatabase\": \"目标数据库\",\n" +
        "  \"targetTable\": \"目标表名\",\n" +
        "  \"sqlContent\": \"生成的SQL语句\",\n" +
        "  \"description\": \"作业描述\"\n" +
        "}\n" +
        "\n" +
        "如果信息不足，请在 \"missingInfo\" 字段中列出需要用户补充的信息。\n" +
        "只返回 JSON，不要有其他说明文字。";

    /**
     * SQL 生成提示词模板
     */
    public static final String SQL_GENERATION_TEMPLATE =
        "你是一个 SQL 专家。请根据用户需求生成 SQL 语句。\n" +
        "\n" +
        "用户需求：{requirement}\n" +
        "数据库类型：{db_type}\n" +
        "表名：{table_name}\n" +
        "表结构：{table_schema}\n" +
        "\n" +
        "请生成标准的 SQL 语句，要求：\n" +
        "1. 语法正确，符合 {db_type} 规范\n" +
        "2. 性能优化，使用合适的索引和查询方式\n" +
        "3. 包含必要的注释\n" +
        "4. 符合最佳实践\n" +
        "\n" +
        "只返回 SQL 语句和必要的注释，不要有其他说明文字。";

    /**
     * 配置提取提示词模板
     */
    public static final String CONFIG_EXTRACTION_TEMPLATE =
        "用户想要创建一个数据作业，请从以下描述中提取关键信息：\n" +
        "\n" +
        "用户描述：{user_input}\n" +
        "\n" +
        "请以 JSON 格式返回：\n" +
        "{\n" +
        "  \"jobName\": \"作业名称\",\n" +
        "  \"sourceTable\": \"源表\",\n" +
        "  \"targetTable\": \"目标表\",\n" +
        "  \"filterCondition\": \"筛选条件\",\n" +
        "  \"needsAggregation\": true/false,\n" +
        "  \"aggregationType\": \"聚合类型（如果需要）\",\n" +
        "  \"timeRange\": \"时间范围（如果有）\"\n" +
        "}\n" +
        "\n" +
        "只返回 JSON，不要其他内容。";

    /**
     * SQL 优化建议提示词模板
     */
    public static final String SQL_OPTIMIZATION_TEMPLATE =
        "你是一个 SQL 性能优化专家。请分析以下 SQL 并给出优化建议。\n" +
        "\n" +
        "SQL 语句：\n" +
        "{sql}\n" +
        "\n" +
        "数据库类型：{db_type}\n" +
        "表信息：{table_info}\n" +
        "\n" +
        "请提供：\n" +
        "1. 性能问题分析\n" +
        "2. 具体优化建议\n" +
        "3. 优化后的 SQL\n" +
        "4. 预期性能提升\n" +
        "\n" +
        "以 JSON 格式返回：\n" +
        "{\n" +
        "  \"issues\": [\"问题1\", \"问题2\"],\n" +
        "  \"suggestions\": [\"建议1\", \"建议2\"],\n" +
        "  \"optimizedSql\": \"优化后的SQL\",\n" +
        "  \"improvement\": \"预期提升百分比\"\n" +
        "}";

    /**
     * 智能问答提示词模板
     */
    public static final String QA_TEMPLATE =
        "你是一个数据平台的技术专家。请回答用户的问题。\n" +
        "\n" +
        "用户问题：{question}\n" +
        "上下文信息：{context}\n" +
        "\n" +
        "请提供：\n" +
        "1. 清晰的回答\n" +
        "2. 如果是故障问题，提供诊断步骤\n" +
        "3. 如果是配置问题，提供具体配置建议\n" +
        "4. 相关的最佳实践\n" +
        "\n" +
        "回答要简洁、专业、易懂。";
}
