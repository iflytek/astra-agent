package com.iflytek.astron.console.toolkit.config.jooq;

import java.util.List;

// New sender interface
@FunctionalInterface
public interface SqlSender {
    void send(String sql, List<Object> params) throws Exception;
}
