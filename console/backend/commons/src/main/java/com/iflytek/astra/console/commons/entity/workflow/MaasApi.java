package com.iflytek.astra.console.commons.entity.workflow;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaasApi {

    // Workflow id
    private String flow_id;

    private JSONObject data;

    // User's appid
    private String app_id;

    // Publish status, enum value, 1: published
    private Integer release_status;

    // 2: Open platform
    private Integer plat;

    private String version;

    public MaasApi(String flow_id, String app_id) {
        this.flow_id = flow_id;
        this.app_id = app_id;
        this.release_status = 1;
        this.plat = 2;
    }

    public MaasApi(String flow_id, String app_id, String version) {
        this.flow_id = flow_id;
        this.app_id = app_id;
        this.version = version;
        this.release_status = 1;
        this.plat = 2;
    }

    public MaasApi(String flow_id, String app_id, String version, JSONObject data) {
        this.flow_id = flow_id;
        this.app_id = app_id;
        this.version = version;
        this.release_status = 1;
        this.plat = 2;
        this.data = data;
    }
}
