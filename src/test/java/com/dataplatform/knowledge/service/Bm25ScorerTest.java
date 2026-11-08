package com.dataplatform.knowledge.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class Bm25ScorerTest {

    private static final double EPS = 1e-5;
    private final Bm25Scorer scorer = new Bm25Scorer(); // k1=1.2, b=0.75

    /**
     * 手算例子：4 篇文档（词已切好），N=4，总长 20，avgdl=5。
     *   d1 = [spark spark 内存 配置]                     len=4
     *   d2 = [spark hive hive 数据 任务 失败]             len=6
     *   d3 = [作业 发布 回滚 版本 管理]                   len=5
     *   d4 = [数据 同步 失败 排查 原因]                   len=5
     * 查询 = [spark, hive]；df(spark)=2 (d1,d2)，df(hive)=1 (d2)
     *
     * IDF = ln(1 + (N - n + 0.5) / (n + 0.5))
     *   idf(spark) = ln(1 + (4-2+0.5)/(2+0.5)) = ln(2)          = 0.693147
     *   idf(hive)  = ln(1 + (4-1+0.5)/(1+0.5)) = ln(10/3)       = 1.203973
     *
     * 项得分 = idf * tf*(k1+1) / (tf + k1*(1 - b + b*len/avgdl))
     *   d1: norm = 1.2*(0.25 + 0.75*4/5) = 1.2*0.85 = 1.02 ... 注意分母用 tf + norm
     *       spark tf=2: 2*2.2/(2+1.02) = 4.4/3.02 = 1.456954 -> *0.693147 = 1.009883
     *       hive 不在 d1 -> 0               => d1 = 1.009883
     *   d2: norm = 1.2*(0.25 + 0.75*6/5) = 1.2*1.15 = 1.38
     *       spark tf=1: 1*2.2/(1+1.38) = 0.924370 -> *0.693147 = 0.640724
     *       hive  tf=2: 2*2.2/(2+1.38) = 1.301775 -> *1.203973 = 1.567302
     *                                       => d2 = 2.208026
     *   d3、d4 不含任何查询词 => 0
     */
    @Test
    void matchesHandComputedExample() {
        Map<String, Long> df = Map.of("spark", 2L, "hive", 1L);
        List<String> q = List.of("spark", "hive");

        assertThat(scorer.idf(4, 2)).isCloseTo(0.693147, within(EPS));
        assertThat(scorer.idf(4, 1)).isCloseTo(1.203973, within(EPS));

        double d1 = scorer.score(q, Map.of("spark", 2, "内存", 1, "配置", 1), 4, 5.0, 4, df);
        double d2 = scorer.score(q, Map.of("spark", 1, "hive", 2), 6, 5.0, 4, df);
        double d3 = scorer.score(q, Map.of("作业", 1), 5, 5.0, 4, df);
        double d4 = scorer.score(q, Map.of(), 5, 5.0, 4, df);

        assertThat(d1).isCloseTo(1.009883, within(EPS));
        assertThat(d2).isCloseTo(2.208026, within(EPS));
        assertThat(d3).isZero();
        assertThat(d4).isZero();
    }

    @Test
    void higherTermFrequencyScoresHigher() {
        Map<String, Long> df = Map.of("spark", 2L);
        double tf1 = scorer.score(List.of("spark"), Map.of("spark", 1), 10, 10.0, 10, df);
        double tf3 = scorer.score(List.of("spark"), Map.of("spark", 3), 10, 10.0, 10, df);
        assertThat(tf3).isGreaterThan(tf1);
    }

    @Test
    void longerDocumentScoresLower() {
        Map<String, Long> df = Map.of("spark", 2L);
        double shortDoc = scorer.score(List.of("spark"), Map.of("spark", 2), 5, 10.0, 10, df);
        double longDoc = scorer.score(List.of("spark"), Map.of("spark", 2), 50, 10.0, 10, df);
        assertThat(shortDoc).isGreaterThan(longDoc);
    }

    @Test
    void noMatchingTermScoresZero() {
        double s = scorer.score(List.of("hive"), Map.of("spark", 3), 10, 10.0, 10, Map.of("hive", 1L));
        assertThat(s).isZero();
    }

    @Test
    void idfIsNeverNegative() {
        // 经典 ln((N-n+0.5)/(n+0.5)) 在 n > N/2 时为负；这里的变体恒为正
        assertThat(scorer.idf(10, 10)).isPositive();
        assertThat(scorer.idf(10, 9)).isPositive();
        assertThat(scorer.idf(1, 1)).isPositive();
        assertThat(scorer.idf(10, 0)).isPositive();
        // 缓存过期导致 n > N 时也不能出现 NaN / 负数
        double stale = scorer.idf(10, 25);
        assertThat(stale).isNotNaN().isPositive();
        assertThat(stale).isCloseTo(scorer.idf(10, 10), within(1e-12));
    }

    @Test
    void duplicateQueryTermsAreCountedOnce() {
        Map<String, Long> df = Map.of("spark", 2L);
        double once = scorer.score(List.of("spark"), Map.of("spark", 2), 10, 10.0, 10, df);
        double twice = scorer.score(List.of("spark", "spark"), Map.of("spark", 2), 10, 10.0, 10, df);
        assertThat(twice).isCloseTo(once, within(1e-12));
    }

    @Test
    void zeroAverageLengthDoesNotProduceNaN() {
        double s = scorer.score(List.of("spark"), Map.of("spark", 1), 0, 0.0, 5, Map.of("spark", 1L));
        assertThat(s).isNotNaN().isPositive();
    }

    @Test
    void missingDocFreqIsTreatedAsZero() {
        double s = scorer.score(List.of("spark"), Map.of("spark", 1), 10, 10.0, 5, Map.of());
        assertThat(s).isNotNaN().isPositive();
    }
}
