package com.iflytek.astron.console.toolkit.entity.table.repo;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @description file_directory_tree
 * @author zhengkai.blog.csdn.net
 * @date 2023-09-04
 */
@Data
public class FileDirectoryTree implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * Primary key is directory
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Directory name
     */
    @TableField("`name`")
    private String name;

    /**
     * Parent directory ID, -1 is root directory
     */
    private Long parentId;

    /**
     * Whether it is a file, 0 is false (default folder), 1 is true (indicates file)
     */
    private Integer isFile;

    /**
     * Associated app ID
     */
    private String appId;

    /**
     * Associated file ID, only valid when current is_file is 1
     */
    private Long fileId;

    /**
     * Remark information, can be synchronized here for information changes
     */
    @TableField("`comment`")
    private String comment;

    /**
     * Create time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    /**
     * Update time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<FileDirectoryTree> children;

    @TableField(exist = false)
    private FileInfoV2 fileInfoV2;

    @TableField(exist = false)
    private String path;
    /**
     * Status, 0: only perform slice, 1: embedding status
     */
    private Integer status;

    /**
     * Hit count
     */
    private Long hitCount;
}
