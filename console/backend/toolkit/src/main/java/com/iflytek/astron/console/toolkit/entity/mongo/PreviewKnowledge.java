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
@Document(collection = "preview_knowledge")
public class PreviewKnowledge {
    @Id
    private String id;
    @Indexed
    private String fileId;

    // Knowledge point
    private JSONObject content;
    private Long charCount;

    // Audit results and details
    /*
     * private String auditRequestId; private String auditSuggest; private JSONArray auditDetail;
     */

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

}
