package com.dataplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DashScope (阿里百炼) AI 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.dashscope")
public class DashScopeProperties {

    /**
     * API Key
     */
    private String apiKey;

    /**
     * API Base URL
     */
    private String baseUrl;

    /**
     * 模型名称
     */
    private String model;
}
