package com.iflytek.astra.console.toolkit.entity.table.group;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
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
 * @since 2024-01-08
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class GroupUser implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;


    /**
     * User ID
     */
    private String uid;


    /**
     * Tag name
     */
    private String userId;


    /**
     * Associated tag
     */
    private Long tagId;


    /**
     * Tag creation time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;



}
