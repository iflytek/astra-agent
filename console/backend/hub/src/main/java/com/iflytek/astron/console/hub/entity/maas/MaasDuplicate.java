package com.iflytek.astron.console.hub.entity.maas;

import com.iflytek.astron.console.commons.entity.bot.BotCreateForm;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MaasDuplicate extends BotCreateForm {

    private Long maasId;

}
