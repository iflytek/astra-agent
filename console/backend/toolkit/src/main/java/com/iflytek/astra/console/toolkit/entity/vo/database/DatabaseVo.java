package com.iflytek.astra.console.toolkit.entity.vo.database;

import com.iflytek.astra.console.toolkit.entity.table.database.DbInfo;
import lombok.Data;

@Data
public class DatabaseVo extends DbInfo {

    String address;

    Long tbNum;

    Long botCount;
}
