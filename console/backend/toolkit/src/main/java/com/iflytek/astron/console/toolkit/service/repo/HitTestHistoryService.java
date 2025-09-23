package com.iflytek.astron.console.toolkit.service.repo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astron.console.toolkit.entity.table.repo.HitTestHistory;
import com.iflytek.astron.console.toolkit.mapper.repo.HitTestHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * Hit Test History Service Implementation
 * </p>
 *
 * @author xxzhang23
 * @since 2023-12-09
 */
@Service
@Slf4j
public class HitTestHistoryService extends ServiceImpl<HitTestHistoryMapper, HitTestHistory> {

    /**
     * Get single record by query wrapper
     *
     * @param wrapper query wrapper
     * @return single HitTestHistory record or null if not found
     */
    public HitTestHistory getOnly(QueryWrapper<HitTestHistory> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }
}
