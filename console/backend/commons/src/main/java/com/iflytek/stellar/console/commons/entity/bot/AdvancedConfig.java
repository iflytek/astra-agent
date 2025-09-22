package com.iflytek.astra.console.commons.entity.bot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class AdvancedConfig {

    private Prologue prologue;

    private String backgroundPic;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Prologue {

        private boolean enabled;

        private String prologueText;

        private List<String> inputExample;
    }

    public AdvancedConfig(String prologueText, List<String> inputExample, String backgroundPic) {
        this.prologue = new Prologue(Boolean.TRUE, prologueText, inputExample);
        this.backgroundPic = backgroundPic;
    }
}
