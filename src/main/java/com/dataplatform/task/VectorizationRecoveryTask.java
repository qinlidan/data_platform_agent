package com.dataplatform.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import com.dataplatform.service.VectorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class VectorizationRecoveryTask implements ApplicationListener<ApplicationReadyEvent> {

    private final KnowledgeChunkMapper chunkMapper;
    private final VectorizationService vectorizationService;

    public VectorizationRecoveryTask(KnowledgeChunkMapper chunkMapper,
                                      VectorizationService vectorizationService) {
        this.chunkMapper = chunkMapper;
        this.vectorizationService = vectorizationService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<Long> pendingIds = chunkMapper.selectList(
            Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getVectorizeStatus, "pending_vectorization")
        ).stream().map(KnowledgeChunk::getId).collect(Collectors.toList());

        if (!pendingIds.isEmpty()) {
            log.warn("检测到 {} 个待向量化 chunks，启动补偿任务", pendingIds.size());
            vectorizationService.vectorizeChunksAsync(pendingIds);
        }
    }
}
