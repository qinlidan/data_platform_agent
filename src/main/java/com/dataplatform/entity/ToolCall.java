package com.dataplatform.entity;

import lombok.Data;

/**
 * 工具调用信息（对齐 OpenAI 格式）
 */
@Data
public class ToolCall {

    /**
     * 工具调用唯一 ID
     */
    private String id;

    /**
     * 类型（固定为 "function"）
     */
    private String type;

    /**
     * 函数调用信息
     */
    private FunctionCall function;

    /**
     * 函数调用详情
     */
    @Data
    public static class FunctionCall {
        /**
         * 函数名
         */
        private String name;

        /**
         * 函数参数（JSON 字符串）
         */
        private String arguments;
    }
}
