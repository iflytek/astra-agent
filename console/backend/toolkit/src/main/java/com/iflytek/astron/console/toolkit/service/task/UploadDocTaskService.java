package com.iflytek.astron.console.toolkit.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astron.console.toolkit.entity.dto.UploadDocTaskDto;
import com.iflytek.astron.console.toolkit.entity.table.repo.UploadDocTask;
import com.iflytek.astron.console.toolkit.mapper.repo.UploadDocTaskMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service implementation for managing document upload tasks Provides functionality for creating,
 * querying, and managing document upload operations
 */
@Service
@Slf4j
public class UploadDocTaskService extends ServiceImpl<UploadDocTaskMapper, UploadDocTask> {

    @Resource
    private UploadDocTaskMapper uploadDocTaskMapper;

    /**
     * Get a single UploadDocTask record with limit 1 using QueryWrapper
     *
     * @param wrapper Query conditions wrapper
     * @return Single UploadDocTask entity or null if not found
     */
    public UploadDocTask getOnly(QueryWrapper<UploadDocTask> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    /**
     * Get a single UploadDocTask record with limit 1 using LambdaQueryWrapper
     *
     * @param wrapper Lambda query conditions wrapper
     * @return Single UploadDocTask entity or null if not found
     */
    public UploadDocTask getOnly(LambdaQueryWrapper<UploadDocTask> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    /**
     * Select upload document task DTOs by source IDs and application ID
     *
     * @param sourcesIds List of source IDs to filter by
     * @param appId Application ID to filter by
     * @return List of UploadDocTaskDto objects matching the criteria
     */
    public List<UploadDocTaskDto> selectUploadDocTaskDtoBySourcesId(List<String> sourcesIds, String appId) {
        return uploadDocTaskMapper.selectUploadDocTaskDtoBySourcesId(sourcesIds, appId);
    }
}
