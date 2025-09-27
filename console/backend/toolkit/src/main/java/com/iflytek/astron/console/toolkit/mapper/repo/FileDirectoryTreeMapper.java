package com.iflytek.astron.console.toolkit.mapper.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.toolkit.entity.table.repo.FileDirectoryTree;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.*;

/**
 * <p>
 * Mapper interface
 * </p>
 *
 * @author xxzhang23
 * @since 2023-09-04
 */
@Mapper


public interface FileDirectoryTreeMapper extends BaseMapper<FileDirectoryTree> {


    // Find file directory list information in idList
    List<FileDirectoryTree> queryListInIdList(@Param("appId") String appId, @Param("idList") List<Long> idList);

    // In idList, the childMaxDeep of file directories can all be increased by 1, used to record depth
    Integer childMaxDeepAutoIncreaseInIdList(@Param("appId") String appId, @Param("idList") Set<Long> idList);

    List<FileDirectoryTree> matchModelListWithDirectoryName(Map<String, Object> map);

    List<Integer> getFileDirectoryTreeIdBySourceId(@Param("sourceIds") List<String> sourceIds);

    List<FileDirectoryTree> getModelListLinkFileInfoV2(Map<Object, Object> map);

    List<FileDirectoryTree> getModelListSearchByFileName(Map<Object, Object> map);

    Integer getModelCountByRepoIdAndFileUUIDS(@Param("repoId") String repoId, @Param("sourceId") String sourceId);

}
