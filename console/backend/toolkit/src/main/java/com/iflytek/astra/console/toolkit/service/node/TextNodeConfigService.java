package com.iflytek.astra.console.toolkit.service.node;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.toolkit.entity.table.node.TextNodeConfig;
import com.iflytek.astra.console.toolkit.mapper.node.TextNodeConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;

/**
 * @Author clliu19
 * @Date: 2025/3/10 09:16
 */
@Service
@Slf4j
public class TextNodeConfigService extends ServiceImpl<TextNodeConfigMapper, TextNodeConfig> {

    public Object saveInfo(TextNodeConfig textNodeConfig) {
        textNodeConfig.setCreateTime(new Date());
        textNodeConfig.setUpdateTime(new Date());
        TextNodeConfig one = this.getOne(new LambdaQueryWrapper<TextNodeConfig>()
                        .eq(TextNodeConfig::getSeparator, textNodeConfig.getSeparator())
                        .in(TextNodeConfig::getUid, Arrays.asList(textNodeConfig.getUid(), -1)));
        if (one != null) {
            log.error("There are duplicate separators present " + textNodeConfig.getSeparator());
            throw new BusinessException(ResponseEnum.DELIMITER_SAME);
        }

        textNodeConfig.setComment(textNodeConfig.getSeparator());
        return this.save(textNodeConfig);
    }
}
