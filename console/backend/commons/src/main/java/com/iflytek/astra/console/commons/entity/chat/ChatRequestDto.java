package com.iflytek.astra.console.commons.entity.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequestDto<T> {

    private String role;

    private T content;

    private String content_type;
    // Academy protocol upgrade, this field is no longer used in the new protocol but kept for backward
    // compatibility
    private ChatContentMeta content_meta;
    // 627 Academy protocol upgrade, added mixed-mode field
    private List<Object> plugins;

    public ChatRequestDto(String role, T content) {
        this.role = role;
        this.content = content;
        // Default
        this.content_type = "text";
    }

    public ChatRequestDto(String role, ChatContentMeta contentMeta) {
        this.role = role;
        this.content_meta = contentMeta;
    }

    public ChatRequestDto(String role, T content, ChatContentMeta contentMeta) {
        this.role = role;
        this.content = content;
        this.content_meta = contentMeta;
    }

    public ChatRequestDto(String role, T content, String image) {
        this.role = role;
        this.content = content;
        this.content_type = image;
    }

    public ChatRequestDto(String role, T content, String image, ChatContentMeta contentMeta) {
        this.role = role;
        this.content = content;
        this.content_type = image;
        this.content_meta = contentMeta;
    }

    public ChatRequestDto(String role, T content, List<Object> plugins) {
        this.role = role;
        this.content = content;
        this.plugins = plugins;
    }

    /**
     * Get content text
     *
     * @return
     */
    public String gotContentString() {
        if (this.content instanceof String) {
            return (String) this.content;
        } else {
            JSONArray jsonArray = JSON.parseArray(JSON.toJSONString(this.content));
            return jsonArray.getJSONObject(jsonArray.size() - 1).getString("text");
        }
    }
}
