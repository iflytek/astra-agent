package com.iflytek.astra.console.toolkit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.table.ConfigInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * Configuration table Mapper interface
 * </p>
 *
 * @author xywang73
 * @since 2022-05-05
 */
@Mapper

public interface ConfigInfoMapper extends BaseMapper<ConfigInfo> {
    List<ConfigInfo> getListByCategory(@Param("category") String category);

    List<ConfigInfo> getListByCategoryAndCode(@Param("category") String category, @Param("code") String code);

    ConfigInfo getByCategoryAndCode(@Param("category") String category, @Param("code") String code);

    /**
     * Get tool/application square tag list
     *
     * @param category
     * @param code
     * @return
     */
    List<ConfigInfo> getTags(@Param("category") String category, @Param("code") String code);
}
