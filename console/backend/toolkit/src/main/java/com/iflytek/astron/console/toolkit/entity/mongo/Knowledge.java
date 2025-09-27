package com.iflytek.astron.console.toolkit.entity.mongo;

import com.alibaba.fastjson2.JSONObject;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "knowledge")
public class Knowledge {
    @Id
    private String id;
    @Indexed
    private String fileId;
    // Knowledge point
    private JSONObject content;
    private Long charCount;
    // Whether enabled: 1: enabled, 0: disabled
    private Integer enabled;
    // Source: 0: default from file parsing, 1: manually added
    private Integer source;

    private Long testHitCount;// Test hit count

    private Long dialogHitCount;// Dialog hit count

    private String coreRepoName;// Core repo name

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

}
