package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 低置信度问题池实体
 *
 * 必须显式声明表名：MyBatis-Plus 默认按类名推导为 low_confidence_question（单数），
 * 而建表脚本里是 low_confidence_questions（复数）。
 * createdAt 不由应用填充，交给数据库 DEFAULT CURRENT_TIMESTAMP；insert 时该字段为 null 会被忽略。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("low_confidence_questions")
public class LowConfidenceQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String rawQuestion;
    private String source;  // retrieval_low_conf, self_check, user_feedback
    private String reason;
    private LocalDateTime createdAt;
}
