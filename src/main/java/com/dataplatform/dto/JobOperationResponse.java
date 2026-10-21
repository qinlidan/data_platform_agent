package com.dataplatform.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 作业操作响应
 * 返回操作结果、数据和后续问题
 */
@Data
@Builder
public class JobOperationResponse {
    /**
     * 操作是否成功
     */
    private Boolean success;

    /**
     * 返回消息
     */
    private String message;

    /**
     * 操作类型（CREATE, UPDATE, PUBLISH, DELETE等）
     */
    private String operationType;

    /**
     * 返回数据
     */
    private Object data;

    /**
     * 缺失的参数
     */
    private List<String> missingParams;

    /**
     * 需要询问的问题
     */
    private String nextQuestion;
}
