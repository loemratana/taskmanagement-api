package com.ratana.task_management_api.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CacheAsideTest {

    private final CacheAside<String, String> cache =
            new CacheAside<>("test", Caffeine.newBuilder().maximumSize(10).build());
    private final AtomicInteger dbReads = new AtomicInteger();

    private String loadFromDb(String value) {
        dbReads.incrementAndGet();
        return value;
    }

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("miss loads from the database and caches; the next read is a hit")
    void missThenHit() {
        assertThat(cache.get("k", () -> loadFromDb("v1"))).isEqualTo("v1");
        assertThat(cache.get("k", () -> loadFromDb("other"))).isEqualTo("v1");
        assertThat(dbReads).hasValue(1);
    }

    @Test
    @DisplayName("null (row not found) is not cached")
    void nullNotCached() {
        assertThat(cache.get("k", () -> loadFromDb(null))).isNull();
        assertThat(cache.get("k", () -> loadFromDb("v1"))).isEqualTo("v1");
        assertThat(dbReads).hasValue(2);
    }

    @Test
    @DisplayName("outside a transaction, invalidate applies at once")
    void invalidateWithoutTransaction() {
        cache.get("k", () -> loadFromDb("old"));
        cache.invalidate("k");
        assertThat(cache.get("k", () -> loadFromDb("new"))).isEqualTo("new");
    }

    @Test
    @DisplayName("inside a transaction, invalidate waits for commit (old value served until then)")
    void invalidateWaitsForCommit() {
        cache.get("k", () -> loadFromDb("old"));

        TransactionSynchronizationManager.initSynchronization();
        cache.invalidateAll();
        // Not committed yet: other requests still see the committed (old) data, cached or not.
        assertThat(cache.get("k", () -> loadFromDb("new"))).isEqualTo("old");

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(cache.get("k", () -> loadFromDb("new"))).isEqualTo("new");
    }

    @Test
    @DisplayName("rolled-back write does not invalidate")
    void rollbackKeepsCache() {
        cache.get("k", () -> loadFromDb("v1"));

        TransactionSynchronizationManager.initSynchronization();
        cache.invalidate("k");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();

        assertThat(cache.get("k", () -> loadFromDb("v2"))).isEqualTo("v1");
    }

    @Test
    @DisplayName("a slow read that loaded old data before a write's invalidation does not cache it")
    void stalePutGuard() {
        String result = cache.get("k", () -> {
            String old = loadFromDb("old");
            // While this read is "slow", a write commits and invalidates.
            cache.invalidate("k");
            return old;
        });
        assertThat(result).isEqualTo("old");   // this caller still gets what it read
        assertThat(cache.get("k", () -> loadFromDb("new"))).isEqualTo("new");   // but it wasn't cached
    }
}
