package com.dataplatform.knowledge.dto;

import lombok.Data;

/** BM25 路从 PostgreSQL 取回的几种行结构（仅在 Mapper 与 HybridSearchService 之间使用）。 */
public final class Bm25Rows {

    private Bm25Rows() {
    }

    /** 候选 chunk，docLength 为 text_tsv 中非空白词项的总数（分词后的文档长度）。 */
    @Data
    public static class Candidate {
        private Long chunkId;
        private String docType;
        private String routeAction;
        private String text;
        private String rerankText;
        private String sectionPath;
        private int docLength;
    }

    /** 某候选中某查询词项的出现次数。 */
    @Data
    public static class TermFreq {
        private Long chunkId;
        private String lexeme;
        private int tf;
    }

    /** 某词项在全库的文档频率 n。 */
    @Data
    public static class TermDocFreq {
        private String lexeme;
        private long docFreq;
    }

    /** 全库统计：文档总数 N 与平均文档长度 avgdl。 */
    @Data
    public static class CorpusStats {
        private long totalDocs;
        private double avgDocLength;
    }
}
