package com.iflytek.astra.console.toolkit.mapper.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.dto.database.FlowDbRelCountDto;
import com.iflytek.astra.console.toolkit.entity.table.relation.FlowDbRel;

import java.util.List;

public interface FlowDbRelMapper extends BaseMapper<FlowDbRel> {


    List<FlowDbRelCountDto> selectCountsByDbIds(List<Long> dbIds);

    void insertBatch(List<FlowDbRel> dbRelList);
}
