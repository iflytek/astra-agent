package com.iflytek.astra.console.toolkit.entity.table.repo;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.sql.Timestamp;


/**
 * <p>
 *
 * </p>
 *
 * @author xxzhang23
 * @since 2023-12-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TagInfoV2 implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;

    private String uid;

    private Long repoId;


    /**
     * Tag name
     */
    @TableField("`name`")
    private String name;


    /**
     * Type 1: Knowledge base 2: Folder 3: File 4: Knowledge chunk
     */
    private Integer type;


    /**
     * Used to isolate tags between different entities
     */
    private String relationId;


    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;



}
