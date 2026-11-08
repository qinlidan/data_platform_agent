package com.dataplatform.service;

import com.dataplatform.entity.Faq;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FaqServiceMigrationTest {

    @Autowired
    private FaqService faqService;

    @Test
    void testDisplayOrderAndIsEnabledColumnsExist() {
        // 创建FAQ测试display_order和is_enabled字段
        Long faqId1 = faqService.createFaq("测试问题1", "测试答案1", 10);
        Long faqId2 = faqService.createFaq("测试问题2", "测试答案2", 5);

        assertNotNull(faqId1);
        assertNotNull(faqId2);

        // 验证listEnabled查询（使用is_enabled和display_order排序）
        List<Faq> enabledFaqs = faqService.listEnabled();

        // 至少包含刚创建的2条
        assertTrue(enabledFaqs.size() >= 2);

        // 验证排序：display_order=5应该在display_order=10之前
        Faq faq2 = enabledFaqs.stream()
            .filter(f -> f.getId().equals(faqId2))
            .findFirst()
            .orElseThrow();
        Faq faq1 = enabledFaqs.stream()
            .filter(f -> f.getId().equals(faqId1))
            .findFirst()
            .orElseThrow();

        int index1 = enabledFaqs.indexOf(faq1);
        int index2 = enabledFaqs.indexOf(faq2);
        assertTrue(index2 < index1, "FAQ2 (display_order=5) should appear before FAQ1 (display_order=10)");

        // 验证is_enabled字段生效
        assertEquals(true, faq1.getIsEnabled());
        assertEquals(true, faq2.getIsEnabled());
    }

    @Test
    void testDisableFaqExcludedFromList() {
        // 创建并禁用FAQ
        Long faqId = faqService.createFaq("待禁用问题", "待禁用答案", 0);
        faqService.updateFaq(faqId, null, null, null, false);

        // 验证getEnabledById返回null
        Faq disabledFaq = faqService.getEnabledById(faqId);
        assertNull(disabledFaq, "Disabled FAQ should not be returned by getEnabledById");
    }

    @Test
    void testUpdateDisplayOrderAndEnabled() {
        // 创建FAQ
        Long faqId = faqService.createFaq("原始问题", "原始答案", 100);

        // 更新display_order和is_enabled
        faqService.updateFaq(faqId, "更新问题", null, 200, false);

        // 直接通过getById查询（不受is_enabled过滤）
        Faq updated = faqService.getById(faqId);

        assertEquals("更新问题", updated.getQuestion());
        assertEquals(200, updated.getDisplayOrder());
        assertEquals(false, updated.getIsEnabled());
    }
}
