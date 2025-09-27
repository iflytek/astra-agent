package com.iflytek.astron.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TableName("chat_file_user")
@Schema(name = "ChatFileUser", description = "User file information")
public class ChatFileUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Document Q&A file ID")
    private String fileId;

    @Schema(description = "Owner UID")
    private String uid;

    @Schema(description = "File URL")
    private String fileUrl;

    @Schema(description = "File name")
    private String fileName;

    @Schema(description = "File size")
    private Long fileSize;

    @Schema(description = "File PDF URL")
    private String filePdfUrl;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "Deletion status: 0 Not deleted, 1 Deleted")
    private Integer deleted;

    @Schema(description = "Client type: 0 Unknown, 1 PC, 2 H5 mainly for statistics")
    private Integer clientType;

    @Schema(description = "Document type: 0 Long document, 1 Long audio, 2 Long video, 3 OCR")
    private Integer businessType;

    @Schema(description = "Display in history knowledge base: 0 Display, 1 Don't display")
    private Integer display;

    @Schema(description = "Document status: 0 Not processed, 1 Processing, 2 Processed, 3 Processing failed")
    private Integer fileStatus;

    @Schema(description = "Frontend maintained unique file key")
    private String fileBusinessKey;

    @Schema(description = "Video external link processing")
    private String extraLink;

    @Schema(description = "Document classification: 1 Spark Document, 2 Zhiwen, refer to light_app_detail.additional_info field")
    private Integer documentType;

    @Schema(description = "Daily upload count per user")
    private Integer fileIndex;

    @Schema(description = "File scenario: related to document_scene_type table")
    private Long sceneTypeId;

    @Schema(description = "Favorites icon display")
    private String icon;

    @Schema(description = "Favorites content source")
    private String collectOriginFrom;

    @Schema(description = "RAG-v2 version task ID")
    private String taskId;
}
