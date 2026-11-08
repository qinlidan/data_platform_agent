package com.dataplatform.knowledge.dto;

import lombok.Data;

/** 人工标注 groundTruth 时用的候选 chunk（text 为前 120 字）。 */
@Data
public class ChunkCandidate {
    private Long chunkId;
    private String docType;
    private String sectionPath;
    private String text;
}
