package com.dataplatform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话视图对象（View Object）
 *
 * 用于前端展示会话列表和详情
 *
 * @author dataplatform
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话信息")
public class ConversationVO {

    @Schema(description = "会话 ID")
    private Long id;

    @Schema(description = "用户 ID")
    private String userId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "会话状态", example = "ACTIVE")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    /**
     * 以下字段为扩展字段（用于会话列表展示）
     */

    @Schema(description = "最后一条消息内容预览（可选）")
    private String lastMessageContent;

    @Schema(description = "最后一条消息时间（可选）")
    private LocalDateTime lastMessageTime;

    @Schema(description = "消息总数（可选）")
    private Integer messageCount;
}
