package com.dataplatform.chunker;

import com.dataplatform.dto.ChunkDTO;

import java.util.List;

/**
 * Chunker接口
 * 定义文档切分器的通用行为
 */
public interface Chunker {
    /**
     * 判断是否可以处理该文档
     * @param markdown Markdown文本
     * @return true表示可以处理
     */
    boolean canHandle(String markdown);

    /**
     * 切分文档
     * @param markdown Markdown文本
     * @param docId 文档ID
     * @param docType 文档类型
     * @return 切分后的chunk列表
     */
    List<ChunkDTO> chunk(String markdown, String docId, String docType);
}
