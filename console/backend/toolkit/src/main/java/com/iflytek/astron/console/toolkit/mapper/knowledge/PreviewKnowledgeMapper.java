package com.iflytek.astron.console.toolkit.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.toolkit.entity.table.knowledge.MysqlPreviewKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PreviewKnowledgeMapper extends BaseMapper<MysqlPreviewKnowledge> {

    /**
     * Query preview knowledge list by fileId
     */
    List<MysqlPreviewKnowledge> findByFileId(@Param("fileId") String fileId);

    /**
     * Delete preview knowledge by fileId
     */
    int deleteByFileId(@Param("fileId") String fileId);

    /**
     * Count preview knowledge entries by fileId
     */
    Long countByFileId(@Param("fileId") String fileId);

    /**
     * Query preview knowledge list by fileId list
     */
    List<MysqlPreviewKnowledge> findByFileIdIn(@Param("fileIds") List<String> fileIds);

    /**
     * Count preview knowledge entries by fileId list
     */
    Long countByFileIdIn(@Param("fileIds") List<String> fileIds);

    /**
     * Query preview knowledge by fileId and audit type
     */
    List<MysqlPreviewKnowledge> findByFileIdInAndAuditType(@Param("fileIds") List<String> fileIds, @Param("auditType") Integer auditType);

    /**
     * Batch insert preview knowledge entries
     */
    int insertBatch(@Param("list") List<MysqlPreviewKnowledge> list);
}
