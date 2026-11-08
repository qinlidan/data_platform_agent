package com.dataplatform.knowledge.service;

import com.dataplatform.knowledge.dto.CitationDetail;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCitationStoreTest {

    private static CitationDetail cite(int index, String text) {
        return CitationDetail.builder().index(index).chunkId((long) index).text(text).sectionPath("路径/" + index).build();
    }

    @Test
    void saveThenFindReturnsSameOrder() {
        InMemoryCitationStore store = new InMemoryCitationStore();
        store.save("1", List.of(cite(1, "a"), cite(2, "b")));

        Optional<List<CitationDetail>> found = store.find("1");
        assertTrue(found.isPresent());
        assertEquals(2, found.get().size());
        assertEquals("a", found.get().get(0).getText());
        assertEquals(2, found.get().get(1).getIndex());
    }

    @Test
    void findUnknownKeyReturnsEmpty() {
        assertTrue(new InMemoryCitationStore().find("nope").isEmpty());
    }

    @Test
    void saveSameKeyOverwritesWholly() {
        InMemoryCitationStore store = new InMemoryCitationStore();
        store.save("1", List.of(cite(1, "old1"), cite(2, "old2"), cite(3, "old3")));
        store.save("1", List.of(cite(1, "new1")));

        List<CitationDetail> found = store.find("1").orElseThrow();
        assertEquals(1, found.size());
        assertEquals("new1", found.get(0).getText());
    }

    @Test
    void evictsLeastRecentlyAccessedWhenOverCapacity() {
        InMemoryCitationStore store = new InMemoryCitationStore(2);
        store.save("a", List.of(cite(1, "a")));
        store.save("b", List.of(cite(1, "b")));
        // 访问 a，使 b 成为最久未访问
        assertTrue(store.find("a").isPresent());
        store.save("c", List.of(cite(1, "c")));

        assertTrue(store.find("a").isPresent());
        assertTrue(store.find("b").isEmpty(), "b 应被淘汰");
        assertTrue(store.find("c").isPresent());
        assertEquals(2, store.size());
    }

    @Test
    void defaultCapacityIs1000() {
        InMemoryCitationStore store = new InMemoryCitationStore();
        for (int i = 0; i < 1500; i++) {
            store.save("k" + i, List.of(cite(1, "x")));
        }
        assertEquals(1000, store.size());
        assertTrue(store.find("k0").isEmpty());
        assertTrue(store.find("k1499").isPresent());
    }

    @Test
    void nullOrBlankKeyIsIgnored() {
        InMemoryCitationStore store = new InMemoryCitationStore();
        store.save(null, List.of(cite(1, "a")));
        store.save("", List.of(cite(1, "a")));
        store.save("  ", List.of(cite(1, "a")));

        assertEquals(0, store.size());
        assertTrue(store.find(null).isEmpty());
        assertTrue(store.find("").isEmpty());
        assertTrue(store.find("  ").isEmpty());
    }

    @Test
    void nullReferencesIsIgnoredAndKeepsPrevious() {
        InMemoryCitationStore store = new InMemoryCitationStore();
        store.save("1", List.of(cite(1, "keep")));
        store.save("1", null);

        assertEquals("keep", store.find("1").orElseThrow().get(0).getText());
    }

    @Test
    void findReturnsImmutableList() {
        InMemoryCitationStore store = new InMemoryCitationStore();
        store.save("1", List.of(cite(1, "a")));

        List<CitationDetail> found = store.find("1").orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> found.add(cite(2, "x")));
        assertThrows(UnsupportedOperationException.class, () -> found.remove(0));
    }

    @Test
    void mutatingCallerListAfterSaveDoesNotAffectStore() {
        InMemoryCitationStore store = new InMemoryCitationStore();
        List<CitationDetail> src = new ArrayList<>();
        src.add(cite(1, "a"));
        store.save("1", src);

        src.add(cite(2, "b"));
        src.get(0).setText("changed");

        List<CitationDetail> found = store.find("1").orElseThrow();
        assertEquals(1, found.size());
        assertEquals("a", found.get(0).getText());
    }

    @Test
    void mutatingReturnedElementDoesNotAffectStore() {
        InMemoryCitationStore store = new InMemoryCitationStore();
        store.save("1", List.of(cite(1, "a")));

        store.find("1").orElseThrow().get(0).setText("hacked");

        assertEquals("a", store.find("1").orElseThrow().get(0).getText());
    }

    @Test
    void concurrentSaveAndFindDoNotThrow() throws Exception {
        InMemoryCitationStore store = new InMemoryCitationStore(50);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int t = 0; t < threads; t++) {
            final int id = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 2000; i++) {
                        String key = "k" + ((id * 31 + i) % 200);
                        if ((i & 1) == 0) {
                            store.save(key, List.of(cite(1, "v" + i), cite(2, "w" + i)));
                        } else {
                            store.find(key).ifPresent(list -> assertEquals(2, list.size()));
                        }
                    }
                } catch (Throwable e) {
                    errors.add(e);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertTrue(errors.isEmpty(), "并发访问出现异常: " + errors);
        assertTrue(store.size() <= 50);
    }
}
