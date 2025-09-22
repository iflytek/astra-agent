package com.iflytek.astra.console.toolkit.entity.finetune;

import lombok.Data;

@Data
public class AlpacaTrainLine {
    // Required fields
    String instruction;
    String output;

    // Non-required fields
    /**
     * User input, optional
     */
    String input = "";

}
