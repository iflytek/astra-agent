package com.iflytek.astron.console.toolkit.entity.vo.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class RepoVO {
    private Long id;
    private String name;
    private String desc;
    private String avatarIcon;// Avatar icon
    private String avatarColor;// Avatar color
    private List<String> tags;
    private String embeddedModel;// Embedding model
    private Integer indexType;// Index type
    private String appId;// appId
    private Integer source;
    private String outerRepoId;// External repo ID passed by client
    private String coreRepoId;// Built by external client using appID_outerRepoId
    private Boolean enableAudit;

    private Integer operType;// 2: Publish 3: Offline 4: Delete
    private Integer visibility;// Visibility 0: Only self visible 1: Partial users visible
    private List<String> uids;
    private String tag;
}
