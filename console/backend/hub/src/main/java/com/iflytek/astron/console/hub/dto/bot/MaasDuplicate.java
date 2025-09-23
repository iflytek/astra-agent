package com.iflytek.astron.console.hub.dto.bot;

import com.iflytek.astron.console.commons.entity.bot.BotCreateForm;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MaasDuplicate extends BotCreateForm {

    private Long maasId;

}
