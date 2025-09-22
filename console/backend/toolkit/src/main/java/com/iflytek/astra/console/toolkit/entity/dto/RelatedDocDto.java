package com.iflytek.astra.console.toolkit.entity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class RelatedDocDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * File auto-increment primary key ID
     */
    private Long id;

    /**
     * File name
     */
    private String datasetIndex;

    /**
     * File name
     */
    private String name;

    /**
     * Character count
     */
    private Integer charCount;

    /**
     * Paragraph count
     */
    private Integer paraCount;

    /**
     * Creation time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * Status: -1 - Deleted, 0 - Unprocessed, 1 - Processing, 2 - Completed, 3 - Failed
     */
    private Integer status;

    private String docUrl;
}
