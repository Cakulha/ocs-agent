package com.ocs.agent.service;

import com.ocs.agent.model.QuestionType;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AnswerParser {

    /**
     * Parse LLM response into the format expected by the OCS frontend.
     */
    public String parse(String llmResponse, QuestionType type, List<String> options) {
        String cleaned = llmResponse.trim();

        return switch (type) {
            case single -> parseSingle(cleaned, options);
            case multiple -> parseMultiple(cleaned);
            case judgement -> parseJudgement(cleaned);
            case completion -> cleaned;
        };
    }

    /**
     * 单选题：提取单个选项字母 (A, B, C, D)
     * 如果包含字母，直接返回；否则按选项内容匹配。
     */
    private String parseSingle(String response, List<String> options) {
        // 尝试提取大写字母
        String letter = extractLetters(response);
        if (letter != null && letter.length() == 1) {
            return letter;
        }

        // 按选项内容匹配
        for (int i = 0; i < options.size(); i++) {
            String optionContent = options.get(i).replaceAll("^[A-Z]\\.\\s*", "");
            String normResponse = normalize(response);
            String normOption = normalize(optionContent);
            if (normResponse.contains(normOption) || normOption.contains(normResponse)) {
                return String.valueOf((char) ('A' + i));
            }
        }

        // 兜底：直接返回 LLM 回复
        return response;
    }

    /**
     * 多选题：提取多个选项字母，用 # 连接
     */
    private String parseMultiple(String response) {
        String cleaned = cleanPunctuation(response);
        StringBuilder result = new StringBuilder();
        for (char c : cleaned.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                if (!result.isEmpty()) {
                    result.append("#");
                }
                result.append(c);
            }
        }
        return result.isEmpty() ? response : result.toString();
    }

    /**
     * 判断题：关键词匹配，返回 "正确" 或 "错误"
     */
    private String parseJudgement(String response) {
        String norm = normalize(response).toLowerCase();

        // 正向关键词
        String[] positive = {"正确", "对", "是", "true", "yes", "√", "1"};
        for (String kw : positive) {
            if (norm.contains(kw)) return "正确";
        }

        // 负向关键词
        String[] negative = {"错误", "错", "否", "false", "no", "×", "0"};
        for (String kw : negative) {
            if (norm.contains(kw)) return "错误";
        }

        return response;
    }

    private String extractLetters(String text) {
        StringBuilder letters = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                letters.append(c);
            }
        }
        return letters.isEmpty() ? null : letters.toString();
    }

    private String cleanPunctuation(String text) {
        return text.replaceAll("[\\s\\[\\]()（）【】「」,，。、！？;；:：\"\"''「」]+", "");
    }

    private String normalize(String text) {
        return text.replaceAll("[\\s，。、！？,.!?（）()【】「」:：;；\"\"]", "");
    }
}
