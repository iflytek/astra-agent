package com.iflytek.astra.console.toolkit.mapper.model;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.table.model.ModelCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


import java.util.List;
import java.util.Map;

@Mapper
public interface ModelCategoryMapper extends BaseMapper<ModelCategory> {
    /**
     * Query model category node collection (including official/custom leaves and their parents), used
     * to build categoryTree
     */
    List<ModelCategory> listByModelId(@Param("modelId") Long modelId);

    /** Get official top-level node (pid=0) for a specific dimension key */
    Long getTopByKey(@Param("key") String key);

    /** Official duplicate check: whether the same key + same name already exists */
    Long findOfficialByKeyAndName(@Param("pid") Long pid, @Param("name") String name);

    /** Custom duplicate check: whether the same key + (tenant) + normalized(name) already exists */
    Long findCustomIdByKeyAndNormalized(@Param("key") String key,
                    @Param("ownerUid") String ownerUid,
                    @Param("name") String name);


    /** Batch binding: official items */
    int batchInsertOfficialRel(@Param("pairs") List<Map<String, Long>> pairs);

    /** Batch binding: custom items */
    int batchInsertCustomRel(@Param("pairs") List<Map<String, Long>> pairs);

    /** Single selection: delete official binding for given key (ensure uniqueness) */
    int deleteOfficialRelByKey(@Param("modelId") Long modelId, @Param("key") String key);

    /** Single selection: delete custom binding for given key (defensive cleanup) */
    int deleteCustomRelByKey(@Param("modelId") Long modelId, @Param("key") String key);

    /**
     * Query all official categories (excluding custom), used to build the complete tree when creating
     * models
     */
    List<ModelCategory> listAllTree();

    Map<String, Object> findCategoryKeyAndDeleteById(Long pid);
}
