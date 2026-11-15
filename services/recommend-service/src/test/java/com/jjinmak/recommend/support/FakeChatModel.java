package com.jjinmak.recommend.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeChatModel implements ChatModel {

    public static final String DEFAULT_CONDITION_RESPONSE = """
            {"recommendation":true,"maxPrice":null,"minPrice":null,"category":null,"manufacturer":null,"keywords":[]}""";
    public static final String DEFAULT_SUMMARY_RESPONSE = "조건에 맞는 경매를 찾았어요.";

    private final List<Prompt> conditionPrompts = new CopyOnWriteArrayList<>();
    private final List<Prompt> summaryPrompts = new CopyOnWriteArrayList<>();
    private volatile String conditionResponse = DEFAULT_CONDITION_RESPONSE;
    private volatile String summaryResponse = DEFAULT_SUMMARY_RESPONSE;
    private volatile boolean failOnConditions;
    private volatile boolean failOnSummary;

    @Override
    public ChatResponse call(Prompt prompt) {
        if (isConditionCall(prompt)) {
            conditionPrompts.add(prompt);
            if (failOnConditions)
                throw new IllegalStateException("조건 정하기 호출 실패");
            return response(conditionResponse);
        }
        summaryPrompts.add(prompt);
        if (failOnSummary)
            throw new IllegalStateException("요약 호출 실패");
        return response(summaryResponse);
    }

    public static boolean isConditionCall(Prompt prompt) {
        return prompt.getOptions() instanceof OpenAiChatOptions options && options.getResponseFormat() != null;
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    public void respondConditions(String json) {
        this.conditionResponse = json;
    }

    public void respondSummary(String text) {
        this.summaryResponse = text;
    }

    public void failOnConditions() {
        this.failOnConditions = true;
    }

    public void failOnSummary() {
        this.failOnSummary = true;
    }

    public List<Prompt> conditionPrompts() {
        return List.copyOf(conditionPrompts);
    }

    public List<Prompt> summaryPrompts() {
        return List.copyOf(summaryPrompts);
    }

    public void reset() {
        conditionPrompts.clear();
        summaryPrompts.clear();
        conditionResponse = DEFAULT_CONDITION_RESPONSE;
        summaryResponse = DEFAULT_SUMMARY_RESPONSE;
        failOnConditions = false;
        failOnSummary = false;
    }
}
