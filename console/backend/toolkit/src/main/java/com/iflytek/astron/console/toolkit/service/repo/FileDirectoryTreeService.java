package com.iflytek.astron.console.toolkit.service.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astron.console.toolkit.entity.table.repo.FileDirectoryTree;
import com.iflytek.astron.console.toolkit.mapper.repo.FileDirectoryTreeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * File Directory Tree Service Implementation
 * </p>
 *
 * @author xxzhang23
 * @since 2023-12-11
 */
@Service
@Slf4j
public class FileDirectoryTreeService extends ServiceImpl<FileDirectoryTreeMapper, FileDirectoryTree> {

    /**
     * Get single record by query wrapper
     *
     * @param wrapper query wrapper
     * @return single FileDirectoryTree record or null if not found
     */
    public FileDirectoryTree getOnly(QueryWrapper<FileDirectoryTree> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    /**
     * Get single record by lambda query wrapper
     *
     * @param wrapper lambda query wrapper
     * @return single FileDirectoryTree record or null if not found
     */
    public FileDirectoryTree getOnly(LambdaQueryWrapper<FileDirectoryTree> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }
}
