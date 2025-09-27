package com.iflytek.astron.console.toolkit.mapper.database;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.toolkit.entity.table.database.DbTableField;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DbTableFieldMapper extends BaseMapper<DbTableField> {

    void insertBatch(List<DbTableField> dbTableFieldList);
}
