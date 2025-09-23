package com.iflytek.astra.console.toolkit.service.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astra.console.toolkit.entity.table.model.ModelCommon;
import com.iflytek.astra.console.toolkit.mapper.model.ModelCommonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author clliu19
 * @Date: 2025/8/18 15:50
 */
@Service
public class ModelCommonService extends ServiceImpl<ModelCommonMapper, ModelCommon> implements IService<ModelCommon> {
    @Autowired
    private ModelCategoryService modelCategoryService;

    /**
     * Get common model list
     *
     * @param uid User ID
     * @param name Query condition
     * @return List of common models
     */
    public List<ModelCommon> getCommonModelList(String uid, String name) {
        LambdaQueryWrapper<ModelCommon> qw = Wrappers.lambdaQuery(ModelCommon.class)
                .eq(ModelCommon::getIsDelete, 0);
        qw.eq(StringUtils.isNotBlank(name), ModelCommon::getName, name);
        if (uid == null) {
            // Only public models
            qw.isNull(ModelCommon::getUid);
        } else {
            // Public models + models for specified uid
            qw.and(w -> w.isNull(ModelCommon::getUid)
                    .or()
                    .eq(ModelCommon::getUid, uid));
        }
        qw.orderByDesc(ModelCommon::getUpdateTime);
        List<ModelCommon> list = this.list(qw);
        for (ModelCommon modelCommon : list) {
            modelCommon.setCategoryTree(modelCategoryService.getTree(modelCommon.getId()));
        }
        return list;
    }
}
