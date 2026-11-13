package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * AI 会话实体
 */
@Data
@TableName("conversations")
public class Conversation {

    /**
     * 会话 ID（雪花算法生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户标识
     */
    private String userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 会话状态
     */
    private ConversationStatus status;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 软删除时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deletedAt;

    /**
     * 摘要全文（拼接所有分段）
     */
    @TableField("summary")
    private String summary;

    /**
     * Layer 0 摘要覆盖到的消息 ID
     */
    @TableField("summary_upto_msg_id")
    private Long summaryUptoMsgId;

    /**
     * Layer 1 原文层起点消息 ID
     */
    @TableField("layer1_from_msg_id")
    private Long layer1FromMsgId;
}
