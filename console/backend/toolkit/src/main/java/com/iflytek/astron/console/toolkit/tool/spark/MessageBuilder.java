package com.iflytek.astron.console.toolkit.tool.spark;

import com.alibaba.fastjson2.JSON;

import com.iflytek.astron.console.toolkit.common.constant.ChatConstant;
import com.iflytek.astron.console.toolkit.entity.spark.*;
import com.iflytek.astron.console.toolkit.entity.spark.request.Chat;
import com.iflytek.astron.console.toolkit.entity.spark.request.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * MessageBuilder for handling interactions with large language models. This utility class provides
 * methods to build requests and generate chat IDs for Spark API communication.
 *
 * @author tctan
 * @since 2023/8/1 15:48
 */

@Slf4j
public class MessageBuilder {


    /**
     * Generates a unique chat ID using UUID.
     *
     * @return a randomly generated UUID string for chat identification
     */
    public static String generateChatId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Builds a Spark API request with default domain.
     *
     * @param msg the message content to be sent
     * @param appId the application ID for authentication
     * @return JSON string representation of the Spark API request
     */
    public static String buildSparkApiRequest(String msg, String appId) {
        return buildSparkApiRequest(msg, appId, null);
    }

    /**
     * Builds a complete Spark API request with specified parameters.
     *
     * @param msg the message content to be sent
     * @param appId the application ID for authentication
     * @param domain the domain for the chat, can be null for default domain
     * @return JSON string representation of the Spark API request
     */
    public static String buildSparkApiRequest(String msg, String appId, String domain) {

        SparkApiProtocol requestDto = new SparkApiProtocol();

        // header
        Header requestHeader = new Header();
        requestHeader.setAppId(appId);
        requestDto.setHeader(requestHeader);

        // parameter
        Parameter requestParameter = new Parameter();
        Chat chat = new Chat();
        if (domain != null) {
            chat.setDomain(domain);
        }
        requestParameter.setChat(chat);
        requestDto.setParameter(requestParameter);

        // payload
        Payload requestPayload = new Payload();
        Message message = new Message();
        List<Text> messageTextList = new ArrayList<>();
        // Add the latest message
        Text thisMessageText = new Text();
        thisMessageText.setRole(ChatConstant.ROLE_USER);
        thisMessageText.setContent(msg);
        messageTextList.add(thisMessageText);
        message.setText(messageTextList);
        requestPayload.setMessage(message);
        requestDto.setPayload(requestPayload);

        return JSON.toJSONString(requestDto);
    }

}
