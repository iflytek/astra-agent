package com.iflytek.astra.console.toolkit.entity.spark.chat;

import com.iflytek.astra.console.toolkit.common.constant.ChatConstant;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatResponse {
    Header header;
    Payload payload;

    public ChatResponse(String chatId, Object content) {
        this.header = new Header();
        header.setStatus(2);
        header.setIsFinish(true);
        header.setMessage("ok");
        header.setCode(0);
        header.setSeq(1);
        this.payload = new Payload();
        Message message = new Message();
        message.setRole(ChatConstant.ROLE_ASSISTANT);
        message.setContent(content);
        message.setType(ChatConstant.TYPE_ANSWER);
        payload.setMessage(message);
        payload.setChatId(chatId);
    }

    public ChatResponse(String chatId, boolean isFinish, int status, Object content) {
        this.header = new Header();
        header.setStatus(status);
        header.setIsFinish(isFinish);
        header.setMessage("ok");
        header.setCode(0);
        header.setSeq(1);
        this.payload = new Payload();
        Message message = new Message();
        message.setRole(ChatConstant.ROLE_ASSISTANT);
        message.setContent(content);
        message.setType(ChatConstant.TYPE_ANSWER);
        payload.setMessage(message);
        payload.setChatId(chatId);
    }

    public ChatResponse(String chatId, String type, Object content) {
        this.header = new Header();
        header.setStatus(2);
        header.setIsFinish(true);
        header.setMessage("ok");
        header.setCode(0);
        header.setSeq(1);
        this.payload = new Payload();
        Message message = new Message();
        message.setRole(ChatConstant.ROLE_ASSISTANT);
        message.setContent(content);
        message.setType(type);
        payload.setMessage(message);
        payload.setChatId(chatId);
    }
}
