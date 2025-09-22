package com.iflytek.astra.console.toolkit.mapper.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.table.repo.FileInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * File information table Mapper interface
 * </p>
 *
 * @author xrli21
 * @since 2023-07-21
 */

@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {

    List<String> getFileNamesBySourceIdListAndAppId(@Param("appId") String appId, @Param("sourceIdList") List<String> sourceIdList);
}
