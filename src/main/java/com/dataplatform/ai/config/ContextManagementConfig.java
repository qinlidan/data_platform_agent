package com.dataplatform.ai.config;

import com.dataplatform.ai.memory.LayeredMessageRenderer;
import com.dataplatform.ai.memory.TokenBudgetCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会话上下文管理配置
 * 注册三层上下文管理相关组件
 */
@Slf4j
@Configuration
public class ContextManagementConfig {

    @Bean
    public TokenBudgetCalculator tokenBudgetCalculator() {
        log.info("[ContextManagement] Initialized TokenBudgetCalculator with budget=4000");
        return new TokenBudgetCalculator(4000); // 默认4000 tokens历史预算
    }

    @Bean
    public LayeredMessageRenderer layeredMessageRenderer(TokenBudgetCalculator calculator) {
        log.info("[ContextManagement] Initialized LayeredMessageRenderer");
        return new LayeredMessageRenderer(calculator);
    }
}
