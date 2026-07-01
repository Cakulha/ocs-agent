package com.ocs.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnswerResponse {
    private int code;
    private String message;
    private AnswerData data;

    public static AnswerResponse success(String question, String answer) {
        AnswerResponse response = new AnswerResponse();
        response.setCode(0);
        response.setMessage("success");
        response.setData(new AnswerData(question, answer));
        return response;
    }

    public static AnswerResponse notFound() {
        AnswerResponse response = new AnswerResponse();
        response.setCode(1);
        response.setMessage("未找到答案");
        return response;
    }

    public static AnswerResponse error(String message) {
        AnswerResponse response = new AnswerResponse();
        response.setCode(-1);
        response.setMessage(message);
        return response;
    }
}
