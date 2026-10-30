package com.dataplatform.ai.audit;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具审计日志实体
 *
 * @author Claude
 * @since 1.0
 */
@Data
public class ToolAuditLog {

    private Long id;
    private String toolName;
    private String toolInput;
    private String toolResult;
    private String status;
    private Long duration;
    private String userId;
    private String conversationId;
    private String errorMessage;
    private LocalDateTime createTime;
}
