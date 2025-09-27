package com.iflytek.astron.console.toolkit.mapper.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.toolkit.entity.table.relation.FlowToolRel;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FlowToolRelMapper extends BaseMapper<FlowToolRel> {

    @Select("SELECT COUNT(DISTINCT ftr.flow_id) FROM \n" +
            "flow_tool_rel ftr\n" +
            "left join workflow w  \n" +
            "on ftr.flow_id  = w.flow_id \n" +
            "WHERE ftr.tool_id = #{toolId} and w.deleted = 0")
    long selectCountByToolId(@Param("toolId") String toolId);

    void insertBatch(List<FlowToolRel> tools);
}
