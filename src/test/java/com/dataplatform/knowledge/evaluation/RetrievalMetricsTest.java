package com.dataplatform.knowledge.evaluation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalMetricsTest {

    private static final double EPS = 1e-9;

    private static List<Long> ids(Long... v) {
        return Arrays.asList(v);
    }

    // ---------- Recall@K ----------

    @Test
    void recall_emptyGroundTruth_returnsZero() {
        assertThat(RetrievalMetrics.recallAtK(ids(1L, 2L), Collections.emptyList(), 5)).isEqualTo(0.0);
    }

    @Test
    void recall_nullGroundTruth_returnsZero() {
        assertThat(RetrievalMetrics.recallAtK(ids(1L, 2L), null, 5)).isEqualTo(0.0);
    }

    @Test
    void recall_emptyRetrieved_returnsZero() {
        assertThat(RetrievalMetrics.recallAtK(Collections.emptyList(), ids(1L), 5)).isEqualTo(0.0);
    }

    @Test
    void recall_nullRetrieved_returnsZero() {
        assertThat(RetrievalMetrics.recallAtK(null, ids(1L), 5)).isEqualTo(0.0);
    }

    @Test
    void recall_kLargerThanRetrieved_usesAllRetrieved() {
        assertThat(RetrievalMetrics.recallAtK(ids(1L, 2L), ids(1L, 2L), 10)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(EPS));
    }

    @Test
    void recall_nonPositiveK_returnsZero() {
        assertThat(RetrievalMetrics.recallAtK(ids(1L), ids(1L), 0)).isEqualTo(0.0);
        assertThat(RetrievalMetrics.recallAtK(ids(1L), ids(1L), -1)).isEqualTo(0.0);
    }

    @Test
    void recall_onlyTopKCounts() {
        // 命中的 3 排在第 3 位，K=2 时不计入
        assertThat(RetrievalMetrics.recallAtK(ids(9L, 8L, 3L), ids(3L), 2)).isEqualTo(0.0);
        assertThat(RetrievalMetrics.recallAtK(ids(9L, 8L, 3L), ids(3L), 3)).isEqualTo(1.0);
    }

    @Test
    void recall_allHit() {
        assertThat(RetrievalMetrics.recallAtK(ids(1L, 2L, 3L), ids(1L, 2L, 3L), 5)).isEqualTo(1.0);
    }

    @Test
    void recall_noneHit() {
        assertThat(RetrievalMetrics.recallAtK(ids(7L, 8L, 9L), ids(1L, 2L), 5)).isEqualTo(0.0);
    }

    @Test
    void recall_partialHit() {
        // 4 个标注命中 1 个
        assertThat(RetrievalMetrics.recallAtK(ids(1L, 9L, 8L), ids(1L, 2L, 3L, 4L), 5)).isCloseTo(0.25, org.assertj.core.data.Offset.offset(EPS));
    }

    @Test
    void recall_duplicateRetrievedIds_notDoubleCounted() {
        // 检索里 1 重复 3 次，只能算命中 1 个；标注 2 个 -> 0.5，而不是 1.5 或 1.0
        assertThat(RetrievalMetrics.recallAtK(ids(1L, 1L, 1L), ids(1L, 2L), 5)).isCloseTo(0.5, org.assertj.core.data.Offset.offset(EPS));
    }

    @Test
    void recall_duplicateGroundTruth_denominatorIsDistinctSize() {
        // 标注 [1,1,2] 去重后大小 2；命中 1 -> 0.5（若不去重会得到 1/3）
        assertThat(RetrievalMetrics.recallAtK(ids(1L, 9L), ids(1L, 1L, 2L), 5)).isCloseTo(0.5, org.assertj.core.data.Offset.offset(EPS));
    }

    // ---------- MRR ----------

    @Test
    void mrr_firstRank_isOne() {
        assertThat(RetrievalMetrics.mrr(ids(5L, 6L, 7L), ids(5L))).isEqualTo(1.0);
    }

    @Test
    void mrr_thirdRank_isOneThird() {
        assertThat(RetrievalMetrics.mrr(ids(9L, 8L, 5L), ids(5L))).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(EPS));
    }

    @Test
    void mrr_noHit_isZero() {
        assertThat(RetrievalMetrics.mrr(ids(9L, 8L, 7L), ids(5L))).isEqualTo(0.0);
    }

    @Test
    void mrr_usesFirstHitAmongMultipleGroundTruth() {
        // 标注 {5, 7}，7 排第 2，5 排第 3 -> 取第一个命中 = 1/2
        assertThat(RetrievalMetrics.mrr(ids(9L, 7L, 5L), ids(5L, 7L))).isEqualTo(0.5);
    }

    @Test
    void mrr_duplicateIdsBeforeHit_positionStillCountsRawRank() {
        // 名次按原始列表位置，重复项占位：第 3 位命中 -> 1/3
        assertThat(RetrievalMetrics.mrr(ids(9L, 9L, 5L), ids(5L))).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(EPS));
    }

    @Test
    void mrr_emptyOrNullInputs_returnZero() {
        assertThat(RetrievalMetrics.mrr(Collections.emptyList(), ids(1L))).isEqualTo(0.0);
        assertThat(RetrievalMetrics.mrr(null, ids(1L))).isEqualTo(0.0);
        assertThat(RetrievalMetrics.mrr(ids(1L), Collections.emptyList())).isEqualTo(0.0);
        assertThat(RetrievalMetrics.mrr(ids(1L), null)).isEqualTo(0.0);
    }
}
