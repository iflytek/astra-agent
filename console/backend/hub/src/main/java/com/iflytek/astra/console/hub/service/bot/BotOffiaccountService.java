package com.iflytek.astra.console.hub.service.bot;

import com.iflytek.astra.console.hub.entity.BotOffiaccount;

import java.util.List;

public interface BotOffiaccountService {

    List<BotOffiaccount> getAccountList(String uid);

}
