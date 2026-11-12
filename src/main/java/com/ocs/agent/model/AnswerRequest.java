package com.ocs.agent.model;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

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

        if (options != null) {
            // 过滤空白选项（如尾部 <br> 产生的空行），避免字母编号与页面真实选项错位
            options = options.stream()
                    .filter(opt -> opt != null && !opt.isBlank())
                    .collect(Collectors.toList());
        }

        if ((type == QuestionType.single || type == QuestionType.multiple)
                && options != null && !options.isEmpty()) {

            // Check if options already have letter prefixes like "A. ", "B. "
            boolean alreadyPrefixed = options.stream()
                    .anyMatch(opt -> opt != null && opt.matches("^[A-Za-z]\\..*"));

            // 始终按当前位置重新编号：前端按字母转索引匹配选项，
            // 若传入的字母因空选项过滤而与位置错位，保留原字母会导致答错
            List<String> prefixed = new ArrayList<>(options.size());
            char letter = 'A';
            for (String opt : options) {
                String content = opt != null ? opt : "";
                if (alreadyPrefixed) {
                    content = content.replaceFirst("^[A-Za-z]\\.\\s*", "");
                }
                prefixed.add(letter + ". " + content);
                letter++;
            }
            options = prefixed;
        }
    }

    /** 是否携带图片（用于判断是否走多模态请求） */
    public boolean hasImages() {
        return images != null && !images.isEmpty();
    }
}
