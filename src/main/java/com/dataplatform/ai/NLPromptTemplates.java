package com.dataplatform.ai;

/**
 * 自然语言处理提示词模板
 * 用于自然语言对话式作业管理
 */
public class NLPromptTemplates {

    /**
     * 意图识别模板
     * 识别用户想要执行的操作类型
     */
    public static final String INTENT_RECOGNITION_TEMPLATE =
        "你是一个数据平台的智能助手。请分析用户的意图，判断用户想要执行什么操作。\n" +
        "\n" +
        "用户输入：{user_input}\n" +
        "\n" +
        "可能的意图类型：\n" +
        "- CREATE_JOB: 用户想创建新的数据作业（包含\"创建\"、\"新建\"、\"添加\"等关键词）\n" +
        "- UPDATE_JOB: 用户想修改已有作业的配置（包含\"修改\"、\"更新\"、\"改成\"等关键词）\n" +
        "- PUBLISH_JOB: 用户想发布作业（包含\"发布\"、\"上线\"等关键词）\n" +
        "- DELETE_JOB: 用户想删除作业（包含\"删除\"、\"移除\"等关键词）\n" +
        "- QUERY_JOB: 用户想查询作业信息（包含\"查询\"、\"查看\"、\"显示\"等关键词）\n" +
        "- CHAT: 普通对话，不涉及具体的作业操作\n" +
        "\n" +
        "请以 JSON 格式返回，只返回 JSON，不要有其他文字：\n" +
        "{\n" +
        "  \"intent\": \"意图类型\",\n" +
        "  \"confidence\": 0.95,\n" +
        "  \"reason\": \"判断理由\"\n" +
        "}";

    /**
     * 提取创建作业参数模板
     * 从用户输入中提取创建作业所需的所有参数
     */
    public static final String EXTRACT_CREATE_JOB_PARAMS_TEMPLATE =
        "你是一个数据平台的智能助手。用户想要创建一个数据作业，请从用户输入中提取作业参数。\n" +
        "\n" +
        "用户输入：{user_input}\n" +
        "\n" +
        "可用数据源列表：\n" +
        "{datasources}\n" +
        "\n" +
        "请提取以下参数并以 JSON 格式返回：\n" +
        "{\n" +
        "  \"jobCode\": \"作业编码（英文，如果用户没说则根据作业名称自动生成，格式如：sync_user_data）\",\n" +
        "  \"jobName\": \"作业名称（中文）\",\n" +
        "  \"jobType\": \"作业类型（DB2HIVE 或 HIVE2HIVE，根据源判断）\",\n" +
        "  \"sourceDatasourceId\": \"源数据源ID（仅当 jobType=DB2HIVE 时需要，从数据源列表中选择）\",\n" +
        "  \"sourceDatabase\": \"源数据库名\",\n" +
        "  \"sourceTable\": \"源表名\",\n" +
        "  \"targetDatabase\": \"目标数据库名\",\n" +
        "  \"targetTable\": \"目标表名\",\n" +
        "  \"engineType\": \"执行引擎（HIVE 或 SPARK，默认 HIVE）\",\n" +
        "  \"description\": \"作业描述（根据用户输入生成一句话描述）\",\n" +
        "  \"missingParams\": [\"缺失的必需参数列表，如果所有参数都齐全则为空数组\"]\n" +
        "}\n" +
        "\n" +
        "判断规则：\n" +
        "- 如果源是 MySQL、Oracle 等数据库，则 jobType=DB2HIVE，需要 sourceDatasourceId\n" +
        "- 如果源是 Hive 表，则 jobType=HIVE2HIVE，不需要 sourceDatasourceId\n" +
        "- 目标始终是 Hive 表\n" +
        "\n" +
        "只返回 JSON，不要有其他说明文字。";

    /**
     * 提取修改作业参数模板
     * 从用户输入中提取要修改的作业信息
     */
    public static final String EXTRACT_UPDATE_JOB_PARAMS_TEMPLATE =
        "你是一个数据平台的智能助手。用户想要修改一个已有作业，请从用户输入中提取参数。\n" +
        "\n" +
        "用户输入：{user_input}\n" +
        "\n" +
        "已有作业列表：\n" +
        "{jobs}\n" +
        "\n" +
        "请提取以下参数并以 JSON 格式返回：\n" +
        "{\n" +
        "  \"jobId\": \"作业ID（从作业列表中匹配）\",\n" +
        "  \"jobCode\": \"作业编码（用户提到的作业标识）\",\n" +
        "  \"jobName\": \"新的作业名称（如果用户要修改）\",\n" +
        "  \"sourceDatabase\": \"新的源数据库（如果用户要修改）\",\n" +
        "  \"sourceTable\": \"新的源表名（如果用户要修改）\",\n" +
        "  \"targetDatabase\": \"新的目标数据库（如果用户要修改）\",\n" +
        "  \"targetTable\": \"新的目标表名（如果用户要修改）\",\n" +
        "  \"engineType\": \"新的执行引擎（如果用户要修改）\",\n" +
        "  \"description\": \"新的描述（如果用户要修改）\",\n" +
        "  \"missingParams\": [\"缺失的参数，至少需要 jobCode 或 jobId\"]\n" +
        "}\n" +
        "\n" +
        "注意：\n" +
        "- 用户必须指定要修改哪个作业（通过 jobCode 或 jobId）\n" +
        "- 只提取用户明确要修改的字段，其他字段不要包含\n" +
        "- 如果用户说\"改成\"、\"换成\"等，提取新值\n" +
        "\n" +
        "只返回 JSON，不要有其他说明文字。";

    /**
     * 提取发布作业参数模板
     * 从用户输入中提取要发布的作业信息
     */
    public static final String EXTRACT_PUBLISH_JOB_PARAMS_TEMPLATE =
        "你是一个数据平台的智能助手。用户想要发布一个作业，请从用户输入中提取参数。\n" +
        "\n" +
        "用户输入：{user_input}\n" +
        "\n" +
        "已有作业列表：\n" +
        "{jobs}\n" +
        "\n" +
        "请提取以下参数并以 JSON 格式返回：\n" +
        "{\n" +
        "  \"jobId\": \"作业ID（从作业列表中匹配）\",\n" +
        "  \"jobCode\": \"作业编码（用户提到的作业标识）\",\n" +
        "  \"version\": \"版本号（如果用户指定了版本，否则为 null 表示发布当前版本）\",\n" +
        "  \"missingParams\": [\"缺失的参数，至少需要 jobCode 或 jobId\"]\n" +
        "}\n" +
        "\n" +
        "只返回 JSON，不要有其他说明文字。";

    /**
     * 提取删除作业参数模板
     * 从用户输入中提取要删除的作业信息
     */
    public static final String EXTRACT_DELETE_JOB_PARAMS_TEMPLATE =
        "你是一个数据平台的智能助手。用户想要删除一个作业，请从用户输入中提取参数。\n" +
        "\n" +
        "用户输入：{user_input}\n" +
        "\n" +
        "已有作业列表：\n" +
        "{jobs}\n" +
        "\n" +
        "请提取以下参数并以 JSON 格式返回：\n" +
        "{\n" +
        "  \"jobId\": \"作业ID（从作业列表中匹配）\",\n" +
        "  \"jobCode\": \"作业编码（用户提到的作业标识）\",\n" +
        "  \"confirmed\": false,\n" +
        "  \"missingParams\": [\"缺失的参数，至少需要 jobCode 或 jobId\"]\n" +
        "}\n" +
        "\n" +
        "注意：\n" +
        "- 删除操作需要用户二次确认\n" +
        "- confirmed 字段表示用户是否已确认删除（初始为 false）\n" +
        "\n" +
        "只返回 JSON，不要有其他说明文字。";

    /**
     * 生成询问问题模板
     * 当参数不完整时，生成友好的询问语句
     */
    public static final String GENERATE_QUESTION_TEMPLATE =
        "你是一个数据平台的智能助手。用户想要执行某个操作，但还缺少一些必需的信息。\n" +
        "\n" +
        "操作类型：{intent}\n" +
        "已提取的参数：{extracted_params}\n" +
        "缺失的参数：{missing_params}\n" +
        "\n" +
        "请生成一个友好、自然的问题，询问用户补充缺失的信息。\n" +
        "\n" +
        "要求：\n" +
        "- 语言自然流畅，像真人对话\n" +
        "- 一次询问所有缺失的参数\n" +
        "- 如果有可选项，列出选项供用户选择\n" +
        "- 提供示例格式帮助用户回答\n" +
        "\n" +
        "只返回问题文本，不要有其他说明。";

    /**
     * 生成操作确认模板
     * 在执行重要操作前，生成确认信息
     */
    public static final String CONFIRM_OPERATION_TEMPLATE =
        "你是一个数据平台的智能助手。用户即将执行一个操作，请生成确认信息。\n" +
        "\n" +
        "操作类型：{intent}\n" +
        "操作参数：{params}\n" +
        "\n" +
        "请生成一个清晰的确认信息，包括：\n" +
        "1. 即将执行的操作\n" +
        "2. 关键参数列表\n" +
        "3. 询问用户是否确认\n" +
        "\n" +
        "格式要求：\n" +
        "- 使用清晰的分点列表\n" +
        "- 突出关键信息\n" +
        "- 友好但正式的语气\n" +
        "- 最后询问：请回复\"确认\"继续，或\"取消\"放弃操作\n" +
        "\n" +
        "只返回确认信息文本，不要有其他说明。";
}
