package com.iflytek.astra.console.toolkit.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astra.console.toolkit.config.properties.RepoAuthorizedConfig;
import com.iflytek.astra.console.toolkit.entity.table.repo.ExtractKnowledgeTask;
import com.iflytek.astra.console.toolkit.mapper.repo.ExtractKnowledgeTaskMapper;
import com.iflytek.astra.console.toolkit.service.repo.KnowledgeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


/**
 * Service implementation for managing knowledge extraction tasks Provides functionality for
 * creating, querying, and managing knowledge extraction operations
 */
@Service
@Slf4j
public class ExtractKnowledgeTaskService extends ServiceImpl<ExtractKnowledgeTaskMapper, ExtractKnowledgeTask> {

    @Resource
    private RepoAuthorizedConfig repoAuthorizedConfig;

    @Resource
    private KnowledgeService knowledgeService;

    /**
     * Get a single ExtractKnowledgeTask record with limit 1 using QueryWrapper
     *
     * @param wrapper Query conditions wrapper
     * @return Single ExtractKnowledgeTask entity or null if not found
     */
    public ExtractKnowledgeTask getOnly(QueryWrapper<ExtractKnowledgeTask> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    /**
     * Get a single ExtractKnowledgeTask record with limit 1 using LambdaQueryWrapper
     *
     * @param wrapper Lambda query conditions wrapper
     * @return Single ExtractKnowledgeTask entity or null if not found
     */
    public ExtractKnowledgeTask getOnly(LambdaQueryWrapper<ExtractKnowledgeTask> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

}
