package com.dataplatform.knowledge.service;

import com.dataplatform.knowledge.dto.CitationDetail;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于内存的引用存储：最多保留 {@value #DEFAULT_MAX_CONVERSATIONS} 个会话，
 * 超出时淘汰最久未访问的会话（LinkedHashMap accessOrder + 同步）。
 * 进程重启后丢失，多实例部署时各实例互不可见；如需持久化请另行实现 {@link CitationStore}。
 */
@Component
public class InMemoryCitationStore implements CitationStore {

    static final int DEFAULT_MAX_CONVERSATIONS = 1000;

    private final Map<String, List<CitationDetail>> store;

    public InMemoryCitationStore() {
        this(DEFAULT_MAX_CONVERSATIONS);
    }

    InMemoryCitationStore(int maxConversations) {
        if (maxConversations < 1) {
            throw new IllegalArgumentException("maxConversations must be >= 1");
        }
        this.store = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<CitationDetail>> eldest) {
                return size() > maxConversations;
            }
        };
    }

    @Override
    public void save(String key, List<CitationDetail> references) {
        if (isBlank(key) || references == null) {
            return;
        }
        List<CitationDetail> copy = copyOf(references);
        synchronized (store) {
            store.put(key, copy);
        }
    }

    @Override
    public Optional<List<CitationDetail>> find(String key) {
        if (isBlank(key)) {
            return Optional.empty();
        }
        List<CitationDetail> stored;
        synchronized (store) {
            stored = store.get(key); // accessOrder=true：get 会刷新访问顺序
        }
        if (stored == null) {
            return Optional.empty();
        }
        // 存储内的元素不对外暴露，返回深拷贝 + 不可变列表
        return Optional.of(copyOf(stored));
    }

    int size() {
        synchronized (store) {
            return store.size();
        }
    }

    private static List<CitationDetail> copyOf(List<CitationDetail> src) {
        List<CitationDetail> out = new ArrayList<>(src.size());
        for (CitationDetail c : src) {
            out.add(c == null ? null : CitationDetail.builder()
                .index(c.getIndex())
                .chunkId(c.getChunkId())
                .text(c.getText())
                .sectionPath(c.getSectionPath())
                .build());
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
