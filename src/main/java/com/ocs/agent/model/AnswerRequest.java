package com.ocs.agent.model;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;

@Data
public class AnswerRequest {
    private String question;
    private List<String> options;
    private QuestionType type;
    /** base64 图片数组（data URL 格式），顺序对应题目中的 [图片N] 占位符，无图片时为 null */
    private List<String> images;

    /**
     * Normalize the request before processing:
     * - Default null type to {@link QuestionType#completion}
     * - For single/multiple choice, add "A. ", "B. ", etc. prefixes if missing
     */
    public void normalize() {
        if (type == null) {
            type = QuestionType.completion;
        }

        if ((type == QuestionType.single || type == QuestionType.multiple)
                && options != null && !options.isEmpty()) {

            // Check if options already have letter prefixes like "A. ", "B. "
            boolean alreadyPrefixed = options.stream()
                    .anyMatch(opt -> opt != null && opt.matches("^[A-Za-z]\\..*"));

            if (!alreadyPrefixed) {
                List<String> prefixed = new ArrayList<>(options.size());
                char letter = 'A';
                for (String opt : options) {
                    prefixed.add(letter + ". " + (opt != null ? opt : ""));
                    letter++;
                }
                options = prefixed;
            }
        }
    }

    /** 是否携带图片（用于判断是否走多模态请求） */
    public boolean hasImages() {
        return images != null && !images.isEmpty();
    }
}
