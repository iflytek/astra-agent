package com.iflytek.astron.console.toolkit.entity.botConfigProtocol;

import lombok.Data;

import java.io.Serializable;

@Data
public class RegularConfig implements Serializable {
    Rag rag = new Rag();
    Match match = new Match();
}
