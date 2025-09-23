package com.iflytek.astron.console.toolkit.mapper.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.toolkit.entity.dto.database.FlowDbRelCountDto;
import com.iflytek.astron.console.toolkit.entity.table.relation.FlowDbRel;

import java.util.List;

public interface FlowDbRelMapper extends BaseMapper<FlowDbRel> {


    List<FlowDbRelCountDto> selectCountsByDbIds(List<Long> dbIds);

    void insertBatch(List<FlowDbRel> dbRelList);
}
