package com.iflytek.astron.console.toolkit.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.toolkit.entity.table.knowledge.MysqlKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeMapper extends BaseMapper<MysqlKnowledge> {

    /**
     * Query knowledge list by fileId
     */
    List<MysqlKnowledge> findByFileId(@Param("fileId") String fileId);

    /**
     * Query knowledge list by fileId and enabled status
     */
    List<MysqlKnowledge> findByFileIdAndEnabled(@Param("fileId") String fileId, @Param("enabled") Integer enabled);

    /**
     * Query knowledge list by fileId and source
     */
    List<MysqlKnowledge> findByFileIdAndSource(@Param("fileId") String fileId, @Param("source") Integer source);

    /**
     * Query knowledge list by fileId list
     */
    List<MysqlKnowledge> findByFileIdIn(@Param("fileIds") List<String> fileIds);

    /**
     * Query knowledge list by fileId list and enabled status
     */
    List<MysqlKnowledge> findByFileIdInAndEnabled(@Param("fileIds") List<String> fileIds, @Param("enabled") Integer enabled);

    /**
     * Count knowledge entries by fileId list
     */
    Long countByFileIdIn(@Param("fileIds") List<String> fileIds);

    /**
     * Update enabled status by fileId
     */
    int updateEnabledByFileId(@Param("fileId") String fileId, @Param("enabled") Integer enabled);

    /**
     * Update enabled status by fileId and old enabled status
     */
    int updateEnabledByFileIdAndOldEnabled(@Param("fileId") String fileId, @Param("oldEnabled") Integer oldEnabled, @Param("newEnabled") Integer newEnabled);

    /**
     * Delete knowledge by fileId
     */
    int deleteByFileId(@Param("fileId") String fileId);

    /**
     * Delete knowledge by fileId list
     */
    int deleteByFileIdIn(@Param("fileIds") List<String> fileIds);

    /**
     * Fuzzy query knowledge by fileId and content
     */
    List<MysqlKnowledge> findByFileIdInAndContentLike(@Param("fileIds") List<String> fileIds, @Param("query") String query);

    /**
     * Query knowledge by fileId and audit type
     */
    List<MysqlKnowledge> findByFileIdInAndAuditType(@Param("fileIds") List<String> fileIds, @Param("auditType") Integer auditType);

    /**
     * Count knowledge entries by fileId
     */
    Long countByFileId(@Param("fileId") String fileId);

    /**
     * Count knowledge entries by fileId and enabled status
     */
    Long countByFileIdAndEnabled(@Param("fileId") String fileId, @Param("enabled") Integer enabled);
}
