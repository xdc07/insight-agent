package com.xiaodoucheng.insightagent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "insight:chat:memory:";
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String key : keys) {
            ids.add(key.substring(KEY_PREFIX.length()));
        }
        return ids;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + conversationId);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode array = objectMapper.readTree(json);
            List<Message> messages = new ArrayList<>();
            for (JsonNode node : array) {
                messages.add(toMessage(node));
            }
            return messages;
        } catch (Exception e) {
            System.out.println("读取对话记忆失败，conversationId=" + conversationId + "：" + e.getMessage());
            return List.of();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        try {
            List<Map<String, Object>> data = new ArrayList<>();
            for (Message message : messages) {
                data.add(toData(message));
            }
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(KEY_PREFIX + conversationId, json, TTL);
        } catch (Exception e) {
            System.out.println("保存对话记忆失败，conversationId=" + conversationId + "：" + e.getMessage());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }

    private Map<String, Object> toData(Message message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", message.getMessageType().name());
        data.put("text", message.getText() == null ? "" : message.getText());
        if (message instanceof AssistantMessage assistant) {
            data.put("toolCalls", assistant.getToolCalls());
        }
        if (message instanceof ToolResponseMessage tool) {
            data.put("responses", tool.getResponses());
        }
        return data;
    }

    private Message toMessage(JsonNode node) {
        String type = node.get("type").asText();
        String text = node.hasNonNull("text") ? node.get("text").asText() : "";
        switch (type) {
            case "USER":
                return new UserMessage(text);
            case "SYSTEM":
                return new SystemMessage(text);
            case "ASSISTANT": {
                JsonNode calls = node.get("toolCalls");
                if (calls != null && calls.size() > 0) {
                    List<AssistantMessage.ToolCall> toolCalls = objectMapper.convertValue(calls,
                            new TypeReference<List<AssistantMessage.ToolCall>>() {});
                    return new AssistantMessage(text, Map.of(), toolCalls);
                }
                return new AssistantMessage(text);
            }
            case "TOOL": {
                JsonNode responses = node.get("responses");
                List<ToolResponseMessage.ToolResponse> list = responses == null ? List.of()
                        : objectMapper.convertValue(responses,
                        new TypeReference<List<ToolResponseMessage.ToolResponse>>() {});
                return new ToolResponseMessage(list);
            }
            default:
                return new UserMessage(text);
        }
    }
}
