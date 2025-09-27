package com.iflytek.astron.console.toolkit.mapper.database;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.toolkit.entity.dto.database.DbTableCountDto;
import com.iflytek.astron.console.toolkit.entity.table.database.DbTable;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Set;


@Mapper
public interface DbTableMapper extends BaseMapper<DbTable> {

    @Select("select * from db_table where db_id = (select id from db_info where db_id = #{dbId}) and name = {table_name}")
    DbTable selectByDbId(@Param("dbId") String dbId, @Param("tableName") String tableName);

    List<DbTableCountDto> selectCountsByDbIds(@Param("dbIds") List<Long> dbIds);


    List<DbTable> selectListByDbIdAndName(@Param("dbId") String dbId, @Param("tableNames") Set<String> tableNames);
}
