package com.iflytek.astron.console.toolkit.entity.biz.modelconfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class TextToSpeech extends Enabled {
    String vcn;
}
