package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversation_summaries")
public class ConversationSummary {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    private Integer seq;

    /**
     * 这段摘要覆盖的起始消息ID（不含）
     */
    @TableField("from_msg_id")
    private Long fromMsgId;

    /**
     * 这段摘要覆盖的结束消息ID（含）
     */
    @TableField("upto_msg_id")
    private Long uptoMsgId;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
