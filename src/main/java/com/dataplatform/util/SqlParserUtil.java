package com.dataplatform.util;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SQL解析工具类
 */
@Slf4j
public class SqlParserUtil {

    /**
     * 从SQL中解析字段列表
     *
     * @param sql SQL语句
     * @return 字段名列表
     */
    public static List<String> parseFieldsFromSql(String sql) {
        List<String> fields = new ArrayList<>();

        if (sql == null || sql.trim().isEmpty()) {
            return fields;
        }

        try {
            Statement statement = CCJSqlParserUtil.parse(extractSelectClause(sql));

            if (statement instanceof Select) {
                Select select = (Select) statement;
                SelectBody selectBody = select.getSelectBody();

                if (selectBody instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBody;
                    for (SelectItem selectItem : plainSelect.getSelectItems()) {
                        if (selectItem instanceof SelectExpressionItem) {
                            // 具体字段
                            SelectExpressionItem expressionItem = (SelectExpressionItem) selectItem;
                            String fieldName;

                            // 优先使用别名
                            if (expressionItem.getAlias() != null) {
                                fieldName = expressionItem.getAlias().getName();
                            } else {
                                // 否则使用表达式
                                fieldName = expressionItem.getExpression().toString();
                                // 去除表名前缀，如 t.name -> name
                                if (fieldName.contains(".")) {
                                    fieldName = fieldName.substring(fieldName.lastIndexOf(".") + 1);
                                }
                            }
                            fields.add(fieldName);
                        } else if (selectItem instanceof AllColumns) {
                            // SELECT * 的情况
                            fields.add("*");
                        } else if (selectItem instanceof AllTableColumns) {
                            // SELECT table.* 的情况
                            AllTableColumns allTableColumns = (AllTableColumns) selectItem;
                            fields.add(allTableColumns.getTable().getName() + ".*");
                        }
                    }
                }
            }
        } catch (JSQLParserException e) {
            log.error("SQL解析失败: {}", e.getMessage(), e);
            throw new IllegalArgumentException("SQL语法错误，解析失败: " + e.getMessage());
        }

        return fields;
    }

    /**
     * 从 Hive INSERT ... SELECT 中提取 SELECT 子句，便于 JSqlParser 解析字段
     *
     * @param sql 原始SQL
     * @return SELECT 子句（若无 INSERT 前缀则原样返回）
     */
    private static String extractSelectClause(String sql) {
        String lower = sql.toLowerCase();
        int insertIdx = lower.indexOf("insert");
        int selectIdx = lower.indexOf("select");
        if (insertIdx >= 0 && selectIdx > insertIdx) {
            return sql.substring(selectIdx);
        }
        return sql;
    }

    /**
     * 验证SQL语法是否正确
     *
     * @param sql SQL语句
     * @return true-语法正确，false-语法错误
     */
    public static boolean validateSql(String sql) {
        try {
            CCJSqlParserUtil.parse(sql);
            return true;
        } catch (JSQLParserException e) {
            log.warn("SQL语法验证失败: {}", e.getMessage());
            return false;
        }
    }

    private static final Pattern SELECT_STAR_PATTERN =
            Pattern.compile("(?i)\\bselect\\s+(?:\\*|\\w+\\s*\\.\\s*\\*)");

    /**
     * 判断 SQL 是否包含 SELECT * 或 table.* 通配符（正则匹配，不依赖完整解析）
     *
     * @param sql SQL语句
     * @return true-包含通配符，false-不包含
     */
    public static boolean containsWildcardSelect(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        return SELECT_STAR_PATTERN.matcher(sql).find();
    }
}
