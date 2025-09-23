package com.iflytek.astron.console.commons.entity.chat;

import lombok.Data;

import java.util.LinkedList;

@Data
public class ChatRequestDtoList {
    private LinkedList<ChatRequestDto> messages = new LinkedList<>();

    /** Concatenate chat history */
    private Integer length;

    private boolean botEdit = false;
}
