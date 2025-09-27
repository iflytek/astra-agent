package com.iflytek.astron.console.hub.service.bot;

import com.iflytek.astron.console.hub.entity.BotOffiaccount;

import java.util.List;

public interface BotOffiaccountService {

    List<BotOffiaccount> getAccountList(String uid);

}
