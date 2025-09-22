package com.iflytek.astra.console.commons.entity.bot;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Objects;

@AllArgsConstructor
@Data
public class BotTag {
    String tagName;
    Integer index;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BotTag botTag = (BotTag) o;
        return Objects.equals(tagName, botTag.tagName);
    }

    // 重写hashCode方法，只基于tagName生成hash值
    @Override
    public int hashCode() {
        return Objects.hash(tagName);
    }
}
