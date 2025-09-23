package com.iflytek.astron.console.toolkit.entity.table.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.sql.Timestamp;

@Data
public class UserFavoriteBot implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long botId;
    /**
     * Usage flag: 1-Favorite, 2-Use
     */
    private Integer useFlag;
    /**
     * Whether deleted: 1-Deleted, 0-Not deleted
     */
    private Boolean deleted;


    /**
     * Create time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createdTime;
}
