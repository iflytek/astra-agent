package com.iflytek.astra.console.commons.dto.user;

import com.iflytek.astra.console.commons.enums.user.WordsTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BotDataParam {

    private String uid;

    private Long botId;

    private Integer num;

    /**
     * {@link WordsTypeEnum}
     */
    private Integer wordsType;

}
