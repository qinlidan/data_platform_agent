package com.dataplatform.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.entity.Faq;
import com.dataplatform.mapper.FaqMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FAQ 服务
 */
@Service
@Slf4j
public class FaqService extends ServiceImpl<FaqMapper, Faq> {

    /**
     * 获取所有启用的 FAQ
     *
     * @return FAQ 列表
     */
    public List<Faq> listEnabled() {
        return lambdaQuery()
                .eq(Faq::getIsEnabled, true)
                .orderByAsc(Faq::getDisplayOrder)
                .orderByDesc(Faq::getCreatedAt)
                .list();
    }

    /**
     * 根据 ID 获取 FAQ
     *
     * @param id FAQ ID
     * @return FAQ，如果不存在或未启用则返回 null
     */
    public Faq getEnabledById(Long id) {
        return lambdaQuery()
                .eq(Faq::getId, id)
                .eq(Faq::getIsEnabled, true)
                .one();
    }

    /**
     * 创建 FAQ
     *
     * @param question     问题
     * @param answer       答案
     * @param displayOrder 显示顺序
     * @return FAQ ID
     */
    public Long createFaq(String question, String answer, Integer displayOrder) {
        Faq faq = new Faq();
        faq.setQuestion(question);
        faq.setAnswer(answer);
        faq.setDisplayOrder(displayOrder != null ? displayOrder : 0);
        faq.setIsEnabled(true);

        LocalDateTime now = LocalDateTime.now();
        faq.setCreatedAt(now);
        faq.setUpdatedAt(now);

        save(faq);
        log.debug("[FaqService] Created FAQ: id={}, question={}", faq.getId(), question);

        return faq.getId();
    }

    /**
     * 更新 FAQ
     *
     * @param id           FAQ ID
     * @param question     问题
     * @param answer       答案
     * @param displayOrder 显示顺序
     * @param enabled      是否启用
     */
    public void updateFaq(Long id, String question, String answer, Integer displayOrder, Boolean enabled) {
        lambdaUpdate()
                .eq(Faq::getId, id)
                .set(question != null, Faq::getQuestion, question)
                .set(answer != null, Faq::getAnswer, answer)
                .set(displayOrder != null, Faq::getDisplayOrder, displayOrder)
                .set(enabled != null, Faq::getIsEnabled, enabled)
                .update();
        log.debug("[FaqService] Updated FAQ: id={}", id);
    }

    /**
     * 删除 FAQ
     *
     * @param id FAQ ID
     */
    public void deleteFaq(Long id) {
        removeById(id);
        log.debug("[FaqService] Deleted FAQ: id={}", id);
    }
}
