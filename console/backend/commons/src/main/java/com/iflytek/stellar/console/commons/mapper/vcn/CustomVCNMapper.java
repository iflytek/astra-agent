package com.iflytek.astra.console.commons.mapper.vcn;

import com.iflytek.astra.console.commons.dto.vcn.CustomV2VCNDTO;
import org.apache.ibatis.annotations.Param;

public interface CustomVCNMapper {

    CustomV2VCNDTO getVcnByCode(@Param("vcnCode") String vcnCode);

}
