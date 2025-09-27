package com.iflytek.astron.console.toolkit.entity.table;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class VcnInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    Long id;

    String vcn;

    String name;

    String style;

    String emt;

    String imageUrl;

    Date createTime;

    Boolean valid;

}
