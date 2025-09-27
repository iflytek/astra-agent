package com.iflytek.astron.console.hub.service.knowledge;

import java.util.List;

/**
 * @author yingpeng Knowledge base related functions
 */
public interface KnowledgeService {

    /**
     * Only retrieve knowledge search content
     */
    List<String> getKnowledge(Long uid, Integer botId, String text, Double threshold);
}
