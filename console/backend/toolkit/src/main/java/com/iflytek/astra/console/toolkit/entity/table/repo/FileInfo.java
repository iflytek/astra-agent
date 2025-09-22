package com.iflytek.astra.console.toolkit.entity.table.repo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
public class FileInfo implements Serializable {
    /**
     * Primary key
     */
    private Long id;
    /**
     * File name
     */
    private String name;
    /**
     * appId
     */
    private String appId;
    /**
     * Storage address
     */
    private String address;
    /**
     * File type
     */
    private String type;
    /**
     * File source ID (used to identify retrieval in vector database)
     */
    private String sourceId;
    /**
     * File size
     */
    private Long size;
    /**
     * Create time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    /**
     * Build status
     */
    private int status;
}
