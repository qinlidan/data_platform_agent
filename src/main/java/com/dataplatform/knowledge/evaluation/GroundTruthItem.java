package com.dataplatform.knowledge.evaluation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * groundTruth 数组里的一项，人工标注。两种形态互斥：
 * <ul>
 *   <li>{@code {"expect_section": "标题,标题,正文片段"}}：正确答案所在的章节（由 {@link SectionResolver} 解析成 chunk_id）；</li>
 *   <li>{@code {"shouldAnswer": false}}：这道题知识库本就不该回答（拒答类），不是召回评估的对象。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroundTruthItem {

    @JsonProperty("expect_section")
    private String expectSection;

    @JsonProperty("shouldAnswer")
    private Boolean shouldAnswer;

    public static GroundTruthItem section(String expectSection) {
        return GroundTruthItem.builder().expectSection(expectSection).build();
    }

    public static GroundTruthItem refusal() {
        return GroundTruthItem.builder().shouldAnswer(false).build();
    }

    /** 是否是"应拒答"标记 */
    public boolean isRefusalMarker() {
        return Boolean.FALSE.equals(shouldAnswer);
    }

    /** 是否带有可用的章节标注 */
    public boolean hasSection() {
        return expectSection != null && !expectSection.isBlank();
    }
}
