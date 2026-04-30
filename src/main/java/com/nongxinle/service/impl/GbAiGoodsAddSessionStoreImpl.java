package com.nongxinle.service.impl;

import com.nongxinle.service.GbAiGoodsAddSessionStore;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class GbAiGoodsAddSessionStoreImpl implements GbAiGoodsAddSessionStore {

    private static final long TTL_MS = 30L * 60 * 1000;

    private final ConcurrentHashMap<String, GoodsAddSessionSnapshot> map = new ConcurrentHashMap<>();

    @Override
    public void put(GoodsAddSessionSnapshot snapshot) {
        map.put(snapshot.sessionId(), snapshot);
    }

    @Override
    public GoodsAddSessionSnapshot get(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        GoodsAddSessionSnapshot s = map.get(sessionId);
        if (s == null) {
            return null;
        }
        if (System.currentTimeMillis() - s.storedAtEpochMs() > TTL_MS) {
            map.remove(sessionId);
            return null;
        }
        return s;
    }
}
