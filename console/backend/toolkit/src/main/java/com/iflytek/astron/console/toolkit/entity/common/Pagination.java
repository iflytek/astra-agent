package com.iflytek.astron.console.toolkit.entity.common;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
public class Pagination implements Serializable {

    private static final long serialVersionUID = -2107467356563726297L;
    private Integer current;
    private Integer pageSize;
    private Integer totalCount;

    public boolean isEmpty() {
        return current == null || pageSize == null;
    }

    public boolean isNotEmpty() {
        return !isEmpty();
    }

}
