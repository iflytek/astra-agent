package com.iflytek.astron.console.toolkit.service.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astron.console.toolkit.entity.table.ConfigInfo;
import com.iflytek.astron.console.toolkit.mapper.ConfigInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class ConfigInfoService extends ServiceImpl<ConfigInfoMapper, ConfigInfo> {
    @Value("${spring.profiles.active}")
    String env;
    private static final String TOOL = "tool";
    private static final String TOOL_V2 = "tool_v2";
    private static final String BOT = "bot";

    /**
     * get tool /app square tag list
     *
     * @return
     */

    public ConfigInfo getOnly(QueryWrapper<ConfigInfo> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    public ConfigInfo getOnly(LambdaQueryWrapper<ConfigInfo> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    public List<ConfigInfo> getTags(String flag) {
        if (TOOL.equals(flag)) {
            return this.getBaseMapper().getTags("TAG", "TOOL_TAGS");
        } else if (BOT.equals(flag)) {
            return this.getBaseMapper().getTags("TAG", "BOT_TAGS");
        } else if (TOOL_V2.equals(flag)) {
            List<ConfigInfo> tags = this.getBaseMapper().getTags("TAG", "TOOL_TAGS_V2");
            if (Arrays.asList("dev", "test").contains(env)) {
                for (ConfigInfo tag : tags) {
                    String remarks = tag.getRemarks();
                    tag.setId(StringUtils.isNotBlank(remarks) ? Long.parseLong(remarks) : tag.getId());
                }
            }
            return tags;
        }
        return Collections.emptyList();
    }

    public List<ConfigInfo> getListByIds(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<ConfigInfo> wrapper = new QueryWrapper<ConfigInfo>().lambda();
        wrapper.in(ConfigInfo::getId, tags);
        wrapper.eq(ConfigInfo::getIsValid, 1);
        return this.list(wrapper);
    }
}
