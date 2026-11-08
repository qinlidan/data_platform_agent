package com.dataplatform.knowledge.service;

import com.dataplatform.knowledge.dto.CitationDetail;

import java.util.List;
import java.util.Optional;

/**
 * 保存"某个会话最近一次回答所用的参考资料"，供前端点击引用编号 [n] 时查询。
 *
 * 契约：
 * - save 的 references 顺序就是回答里 [1]、[2]… 的顺序（下标 0 对应 [1]），调用方保证与 Prompt 中的编号一致。
 * - 同一个 key 再次 save 会整体覆盖（只保留最近一次回答）。
 * - 实现必须有容量上限，不能无限增长。
 */
public interface CitationStore {

    /** @param key 会话标识（RAGService 里用 conversationId 的字符串形式） */
    void save(String key, List<CitationDetail> references);

    /** @return 该会话最近一次回答的全部引用；不存在返回 empty */
    Optional<List<CitationDetail>> find(String key);
}
