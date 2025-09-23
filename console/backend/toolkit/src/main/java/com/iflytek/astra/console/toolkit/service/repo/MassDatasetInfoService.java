package com.iflytek.astra.console.toolkit.service.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astra.console.commons.entity.bot.DatasetInfo;
import com.iflytek.astra.console.commons.entity.dataset.BotDatasetMaas;
import com.iflytek.astra.console.commons.mapper.dataset.BotDatasetMaasMapper;
import com.iflytek.astra.console.toolkit.entity.dto.RepoDto;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class MassDatasetInfoService {

    @Resource
    private BotDatasetMaasMapper botDatasetMaasMapper;

    @Resource
    private RepoService repoService;

    public List<DatasetInfo> getDatasetMaasByBot(String uid, Integer botId, HttpServletRequest request) {
        List<DatasetInfo> infoList = new ArrayList<>();
        List<BotDatasetMaas> botDatasetList = botDatasetMaasMapper.selectList(Wrappers.lambdaQuery(BotDatasetMaas.class)
                .eq(BotDatasetMaas::getBotId, botId)
                .eq(BotDatasetMaas::getIsAct, 1));
        if (Objects.isNull(botDatasetList) || botDatasetList.isEmpty()) {
            return infoList;
        }

        // Set<Long> infoIdSet = botDatasetList.stream()
        // .map(BotDatasetMaas::getDatasetId)
        // .collect(Collectors.toSet());

        botDatasetList.forEach(e -> {
            RepoDto detail = repoService.getDetail(e.getDatasetId(), "", request);
            DatasetInfo datasetInfo = new DatasetInfo();
            datasetInfo.setId(e.getDatasetId());
            datasetInfo.setType(1);
            datasetInfo.setName(detail.getName());
            infoList.add(datasetInfo);
        });
        return infoList;
    }
}
