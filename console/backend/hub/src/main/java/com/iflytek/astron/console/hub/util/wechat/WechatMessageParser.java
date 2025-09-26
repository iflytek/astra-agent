package com.iflytek.astron.console.hub.util.wechat;

import com.iflytek.astron.console.hub.dto.wechat.WechatAuthCallbackDto;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * WeChat Message Parser Utility
 *
 * Optimization features: 1. Unified exception handling 2. Extract common parsing methods 3.
 * Enhanced type safety 4. Simplified API design
 *
 * @author Omuigix
 */
@Slf4j
public class WechatMessageParser {

    /**
     * Parse WeChat authorization success message
     */
    public static WechatAuthCallbackDto parseAuthorizedMessage(String xmlContent) {
        Map<String, String> dataMap = parseXmlToMap(xmlContent);

        return WechatAuthCallbackDto.builder()
                .appId(dataMap.get("AppId"))
                .infoType(dataMap.get("InfoType"))
                .authorizerAppid(dataMap.get("AuthorizerAppid"))
                .authorizationCode(dataMap.get("AuthorizationCode"))
                .authorizationCodeExpiredTime(dataMap.get("AuthorizationCodeExpiredTime"))
                .preAuthCode(dataMap.get("PreAuthCode"))
                .createTime(dataMap.get("CreateTime"))
                .build();
    }

    /**
     * Parse WeChat authorization update message
     */
    public static WechatAuthCallbackDto parseUpdateAuthorizedMessage(String xmlContent) {
        // Authorization update message format is the same as authorization success message
        return parseAuthorizedMessage(xmlContent);
    }

    /**
     * Parse WeChat authorization cancellation message
     */
    public static WechatAuthCallbackDto parseUnauthorizedMessage(String xmlContent) {
        Map<String, String> dataMap = parseXmlToMap(xmlContent);

        return WechatAuthCallbackDto.builder()
                .appId(dataMap.get("AppId"))
                .infoType(dataMap.get("InfoType"))
                .authorizerAppid(dataMap.get("AuthorizerAppid"))
                .createTime(dataMap.get("CreateTime"))
                .build();
    }

    /**
     * Parse verification ticket message
     */
    public static String parseVerifyTicketMessage(String xmlContent) {
        Map<String, String> dataMap = parseXmlToMap(xmlContent);
        return dataMap.get("ComponentVerifyTicket");
    }

    /**
     * Get message type
     */
    public static String getInfoType(String xmlContent) {
        Map<String, String> dataMap = parseXmlToMap(xmlContent);
        return dataMap.get("InfoType");
    }

    /**
     * Parse system message (for extracting encrypted content)
     */
    public static Map<String, String> parseSystemMessage(String xmlContent) {
        return parseXmlToMap(xmlContent, "AppId", "Encrypt");
    }

    /**
     * Parse user message (for extracting encrypted content)
     */
    public static Map<String, String> parseUserMessage(String xmlContent) {
        return parseXmlToMap(xmlContent, "ToUserName", "Encrypt");
    }

    /**
     * Generic XML parsing method
     */
    private static Map<String, String> parseXmlToMap(String xmlContent, String... targetFields) {
        Map<String, String> result = new HashMap<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlContent.getBytes("UTF-8")));

            Element root = document.getDocumentElement();

            if (targetFields.length == 0) {
                // If no fields specified, parse all fields
                NodeList childNodes = root.getChildNodes();
                for (int i = 0; i < childNodes.getLength(); i++) {
                    if (childNodes.item(i) instanceof Element) {
                        Element element = (Element) childNodes.item(i);
                        result.put(element.getTagName(), element.getTextContent());
                    }
                }
            } else {
                // Only parse specified fields
                for (String field : targetFields) {
                    NodeList nodeList = root.getElementsByTagName(field);
                    if (nodeList.getLength() > 0) {
                        result.put(field, nodeList.item(0).getTextContent());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse WeChat XML message: {}", xmlContent, e);
            throw new RuntimeException("WeChat message parsing failed", e);
        }

        return result;
    }
}
