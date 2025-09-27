package com.iflytek.astron.console.toolkit.entity.biz.modelconfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class PresetQuestion extends Enabled {
    List<String> questions;
}
