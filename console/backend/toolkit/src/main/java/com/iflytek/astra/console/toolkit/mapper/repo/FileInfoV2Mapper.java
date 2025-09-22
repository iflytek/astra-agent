package com.iflytek.astra.console.toolkit.mapper.repo;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.table.repo.FileInfoV2;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * Mapper interface
 * </p>
 *
 * @author xxzhang23
 * @since 2023-12-07
 */

@Mapper

public interface FileInfoV2Mapper extends BaseMapper<FileInfoV2> {
    List<FileInfoV2> listByIds(@Param("ids") List<Long> ids);

    List<FileInfoV2> getFileInfoV2UUIDS(@Param("repoSourceId") String repoSourceId, @Param("sourceIds") List<String> sourceIds);

    List<FileInfoV2> getFileInfoV2ByNames(@Param("repoSourceId") String repoCoreId, @Param("fileNames") List<String> fileNames);

    List<FileInfoV2> getFileInfoV2ByRepoId(Long repoId);

    List<FileInfoV2> getFileInfoV2ByCoreRepoId(String coreRepoId);

    List<FileInfoV2> getFileInfoV2byUserId(@Param("uid") String uid);

    List<FileInfoV2> listFiles(@Param("repoId") Long repoId);
}
