package com.dataplatform.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dataplatform.entity.LowConfidenceQuestion;
import com.dataplatform.mapper.LowConfidenceQuestionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 低置信度问题服务
 * 用于收集和管理低置信度问题
 *
 * Task: 低置信度问题池
 */
@Slf4j
@Service
public class LowConfidenceQuestionService {

    @Autowired
    private LowConfidenceQuestionMapper lowConfidenceQuestionMapper;

    /**
     * 记录低置信度问题
     *
     * @param conversationId 会话ID
     * @param rawQuestion    原始问题
     * @param source         来源（retrieval_low_conf / self_check / user_feedback）
     * @param reason         原因描述
     */
    public void recordLowConfidenceQuestion(Long conversationId, String rawQuestion,
                                           String source, String reason) {
        log.info("记录低置信度问题: conversationId={}, source={}, question={}",
                conversationId, source, rawQuestion);

        LowConfidenceQuestion question = LowConfidenceQuestion.builder()
                .conversationId(conversationId)
                .rawQuestion(rawQuestion)
                .source(source)
                .reason(reason)
                .build();

        lowConfidenceQuestionMapper.insert(question);
    }

    /**
     * 查询会话的所有低置信度问题
     *
     * @param conversationId 会话ID
     * @return 低置信度问题列表
     */
    public List<LowConfidenceQuestion> getByConversationId(Long conversationId) {
        QueryWrapper<LowConfidenceQuestion> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId);
        return lowConfidenceQuestionMapper.selectList(wrapper);
    }

    /**
     * 按来源查询低置信度问题
     *
     * @param source 来源
     * @return 低置信度问题列表
     */
    public List<LowConfidenceQuestion> getBySource(String source) {
        QueryWrapper<LowConfidenceQuestion> wrapper = new QueryWrapper<>();
        wrapper.eq("source", source);
        return lowConfidenceQuestionMapper.selectList(wrapper);
    }
}
