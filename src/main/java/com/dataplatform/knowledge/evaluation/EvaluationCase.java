package com.dataplatform.knowledge.evaluation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一条带标注的评估样例。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationCase {
    /** 用户问题 */
    private String query;
    /** 难度：easy | medium | hard */
    private String difficulty;
    /** 旧字段，兼容旧 JSON：keyword | semantic | hybrid。category 缺失时退回用它分桶 */
    private String queryType;
    /** 问题类别：colloquial（口语化）| platform（平台问题）| error_keyword（专业报错关键字）。评估结果按它分桶 */
    private String category;
    /** 用例所属文档类型，仅用于页面分组展示，不参与检索过滤 */
    private String docType;
    /**
     * 人工标注。每项是 {"expect_section": "..."}（正确答案所在章节）或 {"shouldAnswer": false}（应拒答）。
     * 评估时由 {@link SectionResolver} 把章节解析成 chunk_id，所以重新入库后不用重新标注。
     */
    private List<GroundTruthItem> groundTruth;

    /** 分桶用的类别：category → queryType → "unknown"（空白视为缺失）。 */
    public String resolvedCategory() {
        if (category != null && !category.isBlank()) {
            return category;
        }
        if (queryType != null && !queryType.isBlank()) {
            return queryType;
        }
        return "unknown";
    }

    /** 是否有可用于召回打分的标准答案：至少一项带章节标注（空、全是拒答标记、全是 null 都算没有）。 */
    public boolean hasGroundTruth() {
        return groundTruth != null
            && groundTruth.stream().anyMatch(g -> g != null && g.hasSection());
    }

    /** 标注为"应拒答"的题（不参与召回评估，留给拒答阈值校准）。 */
    public boolean isRefusalCase() {
        return groundTruth != null
            && groundTruth.stream().anyMatch(g -> g != null && g.isRefusalMarker())
            && !hasGroundTruth();
    }

    /** 取出全部章节标注文字（跳过 null / 拒答标记 / 空白）。 */
    public List<String> expectSections() {
        if (groundTruth == null) {
            return List.of();
        }
        return groundTruth.stream()
            .filter(g -> g != null && g.hasSection())
            .map(GroundTruthItem::getExpectSection)
            .toList();
    }
}
