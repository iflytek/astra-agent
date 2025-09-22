package com.iflytek.astra.console.toolkit.mapper.repo;



import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.pagehelper.Page;
import com.iflytek.astra.console.toolkit.entity.dto.RepoDto;
import com.iflytek.astra.console.toolkit.entity.table.repo.Repo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


/**
 * <p>
 * Mapper interface
 * </p>
 *
 * @author xxzhang23
 * @since 2023-12-06
 */

@Mapper

public interface RepoMapper extends BaseMapper<Repo> {
    List<String> listCoreRepoIdByRepoId(@Param("appId") String appId);

    List<Repo> listInRepoCoreIds(@Param("coreRepoIds") List<String> coreRepoIds);

    List<RepoDto> list(@Param("userId") String userId, @Param("spaceId") Long spaceId, @Param("includeIds") List<Long> includeIds, @Param("content") String content, @Param("orderBy") String orderBy);

    // List<Repo> getModelListByCondition(@Param("userId") String userId, @Param("includeIds")
    // List<Long>
    // includeIds,@Param("content") String content, @Param("start") Integer start, @Param("limit")
    // Integer limit);

    Page<RepoDto> getModelListByCondition(@Param("userId") String userId, @Param("spaceId") Long spaceId, @Param("includeIds") List<Long> includeIds, @Param("content") String content);

    int getModelListCountByCondition(@Param("userId") String userId, @Param("includeIds") List<Long> includeIds, @Param("content") String content);

    List<Repo> getListInUuids(@Param("list") List<String> repoUuids);
}
