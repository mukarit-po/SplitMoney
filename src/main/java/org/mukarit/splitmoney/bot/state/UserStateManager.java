package org.mukarit.splitmoney.bot.state;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserStateManager {
    private final Map<Long, BotState> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Object>> userData = new ConcurrentHashMap<>();

    public void setState(Long userId, BotState state) {
        userStates.put(userId, state);
    }

    public BotState getState(Long userId) {
        return userStates.getOrDefault(userId, BotState.DEFAULT);
    }

    public void setData(Long userId, String key, Object value) {
        userData.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public Object getData(Long userId, String key) {
        Map<String, Object> data = userData.get(userId);
        return data != null ? data.get(key) : null;
    }

    public void clear(Long userId) {
        userStates.remove(userId);
        userData.remove(userId);
    }
}
