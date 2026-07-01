package com.ocs.agent.service;

import com.ocs.agent.model.QuestionType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnswerParserTest {

    private final AnswerParser parser = new AnswerParser();

    @Test
    void testParseSingleLetter() {
        List<String> options = List.of("A. 为人民服务", "B. 实现共产主义", "C. 依法治国", "D. 改革开放");
        assertEquals("A", parser.parse("A", QuestionType.single, options));
        assertEquals("B", parser.parse("B", QuestionType.single, options));
        assertEquals("C", parser.parse("C", QuestionType.single, options));
        assertEquals("D", parser.parse("D", QuestionType.single, options));
    }

    @Test
    void testParseSingleContentMatch() {
        List<String> options = List.of("A. 为人民服务", "B. 实现共产主义", "C. 依法治国", "D. 改革开放");
        assertEquals("A", parser.parse("为人民服务", QuestionType.single, options));
        assertEquals("B", parser.parse("实现共产主义", QuestionType.single, options));
    }

    @Test
    void testParseSingleWithNoise() {
        List<String> options = List.of("A. 为人民服务", "B. 实现共产主义", "C. 依法治国", "D. 改革开放");
        assertEquals("A", parser.parse("答案是 A", QuestionType.single, options));
        assertEquals("B", parser.parse("正确答案是B", QuestionType.single, options));
    }

    @Test
    void testParseMultipleLetters() {
        List<String> options = List.of("A. 选项一", "B. 选项二", "C. 选项三", "D. 选项四");
        assertEquals("A#C#D", parser.parse("A, C, D", QuestionType.multiple, options));
        assertEquals("A#B", parser.parse("A和B", QuestionType.multiple, options));
        assertEquals("B#D", parser.parse("正确答案：B、D", QuestionType.multiple, options));
    }

    @Test
    void testParseMultipleWithHashInResponse() {
        List<String> options = List.of("A. 选项一", "B. 选项二", "C. 选项三");
        assertEquals("A#C", parser.parse("A#C", QuestionType.multiple, options));
    }

    @Test
    void testParseJudgementPositive() {
        List<String> options = List.of("A. 正确", "B. 错误");
        assertEquals("正确", parser.parse("正确", QuestionType.judgement, options));
        assertEquals("正确", parser.parse("对", QuestionType.judgement, options));
        assertEquals("正确", parser.parse("true", QuestionType.judgement, options));
        assertEquals("正确", parser.parse("√", QuestionType.judgement, options));
    }

    @Test
    void testParseJudgementNegative() {
        List<String> options = List.of("A. 正确", "B. 错误");
        assertEquals("错误", parser.parse("错误", QuestionType.judgement, options));
        assertEquals("错误", parser.parse("错", QuestionType.judgement, options));
        assertEquals("错误", parser.parse("false", QuestionType.judgement, options));
        assertEquals("错误", parser.parse("×", QuestionType.judgement, options));
    }

    @Test
    void testParseCompletion() {
        List<String> options = List.of();
        assertEquals("为人民服务", parser.parse("为人民服务", QuestionType.completion, options));
        assertEquals("人民性", parser.parse("人民性", QuestionType.completion, options));
    }

    @Test
    void testParseEmptyOptions() {
        List<String> options = List.of();
        assertEquals("42", parser.parse("42", QuestionType.completion, options));
    }
}
