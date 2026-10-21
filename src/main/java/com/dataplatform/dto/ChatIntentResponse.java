package com.dataplatform.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 聊天意图响应
 * 返回识别出的意图、实体和后续问题
 */
@Data
public class ChatIntentResponse {
    /**
     * 识别的意图类型
     */
    private String intent;

    /**
     * 置信度
     */
    private Double confidence;

    /**
     * 提取的实体
     */
    private Map<String, Object> entities;

    /**
     * 缺失的参数
     */
    private List<String> missingParams;

    /**
     * 需要询问的下一个问题
     */
    private String nextQuestion;
}
