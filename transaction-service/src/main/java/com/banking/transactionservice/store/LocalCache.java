package com.banking.transactionservice.store;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalCache {

    private final Map<String, Entry> values = new ConcurrentHashMap<>();

    public void set(String key, String value, Duration ttl) {
        values.put(key, new Entry(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    public String get(String key) {
        Entry entry = values.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt() < System.currentTimeMillis()) {
            values.remove(key);
            return null;
        }
        return entry.value();
    }

    public void delete(String key) {
        values.remove(key);
    }

    public long increment(String key, Duration ttl) {
        cleanup(key);
        Entry current = values.get(key);
        long next = 1;
        if (current != null && current.expiresAt() >= System.currentTimeMillis()) {
            next = Long.parseLong(current.value()) + 1;
            values.put(key, new Entry(Long.toString(next), current.expiresAt()));
        } else {
            values.put(key, new Entry("1", System.currentTimeMillis() + ttl.toMillis()));
        }
        return next;
    }

    private void cleanup(String key) {
        Entry entry = values.get(key);
        if (entry != null && entry.expiresAt() < System.currentTimeMillis()) {
            values.remove(key);
        }
    }

    private record Entry(String value, long expiresAt) {
    }
}
