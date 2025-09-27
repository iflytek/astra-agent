package com.iflytek.astron.console.toolkit.entity.vo.database;

import com.iflytek.astron.console.toolkit.entity.table.database.DbInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DatabaseVo extends DbInfo {

    String address;

    Long tbNum;

    Long botCount;
}
