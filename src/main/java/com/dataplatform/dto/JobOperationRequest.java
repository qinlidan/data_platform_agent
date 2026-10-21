package com.dataplatform.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 作业操作请求
 * 用于执行作业相关操作的请求
 */
@Data
public class JobOperationRequest {
    /**
     * 用户输入消息
     */
    @NotBlank(message = "用户输入不能为空")
    private String userInput;

    /**
     * 会话ID(可选)
     */
    private String conversationId;

    /**
     * 上下文信息(可选)
     */
    private Map<String, Object> context;
}
