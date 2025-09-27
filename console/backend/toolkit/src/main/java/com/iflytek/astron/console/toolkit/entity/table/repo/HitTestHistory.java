package com.iflytek.astron.console.toolkit.entity.table.repo;

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
 * @since 2023-12-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class HitTestHistory implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * User ID
     */
    private String userId;


    /**
     * Knowledge base ID
     */
    private Long repoId;


    /**
     * Query string
     */
    @TableField("`query`")
    private String query;


    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;



}
