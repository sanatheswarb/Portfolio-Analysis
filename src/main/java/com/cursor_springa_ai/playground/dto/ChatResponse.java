package com.cursor_springa_ai.playground.dto;

public class ChatResponse {

    private final String answer;
    private final Long chatId;
    private final Long analysisId;

    public ChatResponse(String answer, Long chatId, Long analysisId) {
        this.answer = answer;
        this.chatId = chatId;
        this.analysisId = analysisId;
    }

    public String getAnswer() {
        return answer;
    }

    public Long getChatId() {
        return chatId;
    }

    public Long getAnalysisId() {
        return analysisId;
    }
}
