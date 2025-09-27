package com.iflytek.astron.console.toolkit.entity.biz;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QaData {
    /**
     * sid
     */
    String sid;
    // Question
    String question;
    // Answer
    String answer;
    /**
     * Expected answer
     */
    String expectedAnswer;

    Integer statusCode;

    // Concatenated parameters {"AGENT_USER_INPUT":"hello", "name": "Bob"}
    JSONObject parameters;

    Integer seq;

    public QaData(String sid, String question, String expectedAnswer) {
        this.sid = sid;
        this.question = question;
        this.expectedAnswer = expectedAnswer;
    }

    public QaData(String sid, String question, String answer, Integer statusCode) {
        this.sid = sid;
        this.question = question;
        this.answer = answer;
        this.statusCode = statusCode;
    }

    public QaData(String sid, String question, String answer, String expectedAnswer) {
        this.sid = sid;
        this.question = question;
        this.answer = answer;
        this.expectedAnswer = expectedAnswer;
    }

    public QaData(String sid, String expectedAnswer, JSONObject parameters) {
        this.sid = sid;
        this.expectedAnswer = expectedAnswer;
        this.parameters = parameters;
    }
}
