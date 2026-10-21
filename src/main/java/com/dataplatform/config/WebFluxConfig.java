package com.dataplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux 配置：禁用 SSE 自动编码器
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // 禁用默认的 SSE encoder（它会给每一行加 data: 前缀）
        configurer.defaultCodecs().enableLoggingRequestDetails(true);
    }
}
