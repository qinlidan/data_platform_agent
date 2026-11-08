package com.dataplatform.service;

import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.knowledge.repository.KnowledgeVectorRepository;
import com.dataplatform.knowledge.service.EmbeddingService;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 双写测试：MySQL chunk -> pgvector 时，text / rerank_text / section_path 必须一起写入。
 * 对应 plan Task 2 / Review Focus #2。
 */
@ExtendWith(MockitoExtension.class)
class VectorizationServiceDualWriteTest {

    @Mock private KnowledgeChunkMapper chunkMapper;
    @Mock private EmbeddingService embeddingService;
    @Mock private KnowledgeVectorRepository vectorRepository;

    private VectorizationService service;

    @BeforeEach
    void setUp() {
        service = new VectorizationService(chunkMapper, embeddingService, vectorRepository);
        // 部分用例只测字符串拼接、不触发向量化，故用 lenient
        lenient().when(embeddingService.embedDocument(anyString())).thenReturn(new float[1024]);
    }

    private KnowledgeChunk chunk(Long id, String sceneTitle, String questions, String answer, String sectionPath) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setId(id);
        c.setDocType("spark调优");
        c.setCategory("Spark参数调优");
        c.setSceneTitle(sceneTitle);
        c.setQuestions(questions);
        c.setAnswer(answer);
        c.setSectionPath(sectionPath);
        c.setRouteAction("tunable");
        return c;
    }

    @Test
    void textRerankTextAndSectionPathAreWrittenToPgvector() {
        when(chunkMapper.selectBatchIds(anyList())).thenReturn(List.of(
            chunk(1L, "作业慢", "小文件过多", "调整 spark.sql.files.maxRecordsPerFile", "Spark参数调优 / 作业慢 / 小文件过多")));

        service.retryVectorization(List.of(1L));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> rerankText = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sectionPath = ArgumentCaptor.forClass(String.class);
        verify(vectorRepository).upsert(eq(1L), anyString(), eq("spark调优"), eq("tunable"),
            text.capture(), rerankText.capture(), sectionPath.capture(), any(), any());

        // text = doc_type + scene_title + questions + answer
        assertEquals("spark调优 作业慢 小文件过多 调整 spark.sql.files.maxRecordsPerFile", text.getValue());
        // rerank_text = scene_title + questions + answer（不含 doc_type）
        assertEquals("作业慢 小文件过多 调整 spark.sql.files.maxRecordsPerFile", rerankText.getValue());
        assertEquals("Spark参数调优 / 作业慢 / 小文件过多", sectionPath.getValue());
    }

    @Test
    void nullFieldsDoNotProduceLiteralNullOrStraySpaces() {
        when(chunkMapper.selectBatchIds(anyList())).thenReturn(List.of(
            chunk(2L, null, "小文件过多", "答案", null)));

        service.retryVectorization(List.of(2L));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> rerankText = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sectionPath = ArgumentCaptor.forClass(String.class);
        verify(vectorRepository).upsert(eq(2L), anyString(), anyString(), anyString(),
            text.capture(), rerankText.capture(), sectionPath.capture(), any(), any());

        assertEquals("spark调优 小文件过多 答案", text.getValue());
        assertEquals("小文件过多 答案", rerankText.getValue());
        assertNull(sectionPath.getValue());
    }

    @Test
    void embeddingTextExcludesCategoryAndSymptom() {
        KnowledgeChunk c = chunk(4L, "作业慢", "小文件过多", "调整参数", "p");
        c.setSymptomText("任务耗时翻倍");

        assertEquals("作业慢 | 问法: 小文件过多 | 说明: 调整参数", service.buildEmbeddingText(c));
    }

    @Test
    void embeddingTextSkipsBlankPartsWithoutStraySeparators() {
        KnowledgeChunk c = chunk(5L, null, "小文件过多", "  ", "p");

        assertEquals("问法: 小文件过多", service.buildEmbeddingText(c));
    }

    @Test
    void embeddingServiceReceivesTheNewText() {
        when(chunkMapper.selectBatchIds(anyList())).thenReturn(List.of(
            chunk(6L, "作业慢", "小文件过多", "调整参数", "p")));

        service.retryVectorization(List.of(6L));

        verify(embeddingService).embedDocument("作业慢 | 问法: 小文件过多 | 说明: 调整参数");
    }

    @Test
    void legacyUpsertIsNoLongerUsedByVectorization() {
        when(chunkMapper.selectBatchIds(anyList())).thenReturn(List.of(chunk(3L, "t", "q", "a", "p")));

        service.retryVectorization(List.of(3L));

        // 旧的 6 参 upsert 不写新列，向量化路径不能再走它
        verify(vectorRepository, never()).upsert(any(), anyString(), anyString(), anyString(), any(), any());
    }
}
