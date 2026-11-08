package com.dataplatform.task;

import com.dataplatform.service.KnowledgeImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KnowledgeBaseInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final KnowledgeImportService knowledgeImportService;

    public KnowledgeBaseInitializer(KnowledgeImportService knowledgeImportService) {
        this.knowledgeImportService = knowledgeImportService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("开始自动导入知识库文档");
//        knowledgeImportService.autoImportFromDataQuery();
    }
}
