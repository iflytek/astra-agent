package com.iflytek.astron.console.commons.entity.bot;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class BotDetail {
    private Integer id;
    private String prompt;
    private Integer supportContext;
    private String uid;
    private Integer botType;
    private String botName;
    private String botNameEn;
    private String avatar;
    private String pcBackground;
    private String appBackground;
    private String prologue;
    private String botDesc;
    private String model;
    private String maasBotId;
    private String botDescEn;
    private String botTemplate;
    private String promptType;
    private String inputExample;
    private String vcnCn;
    private String vcnEn;
    private Integer vcnSpeed;
    private String version;
    private String openedTool;
    private Integer hotNum;
    private String marketBotId;
    private Integer supportSystem;
    private Integer supportUpload;
    private Integer botStatus;
    private Long spaceId;
    private Long modelId;
    private List<String> inputExampleList;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * Parse inputExample string to inputExampleList manually Call this method when you need to populate
     * inputExampleList
     */
    public void parseInputExampleList() {
        this.inputExampleList = parseInputExamples(this.inputExample);
    }

    /**
     * Parse inputExample string to list using same logic as BotServiceImpl
     */
    private List<String> parseInputExamples(String inputExample) {
        if (inputExample == null || inputExample.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Use same parsing logic as BotServiceImpl
        String separator = "%%split%%";
        if (!inputExample.contains(separator)) {
            inputExample = inputExample.replace(",", separator);
        }

        return Arrays.stream(inputExample.split(separator))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }


}
