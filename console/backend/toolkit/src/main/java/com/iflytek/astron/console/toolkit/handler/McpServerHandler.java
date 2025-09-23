package com.iflytek.astron.console.toolkit.handler;

import com.alibaba.fastjson2.*;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astron.console.toolkit.entity.common.PageData;
import com.iflytek.astron.console.toolkit.entity.tool.McpServerTool;
import com.iflytek.astron.console.toolkit.util.OkHttpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @Author clliu19
 * @Date: 2025/4/3 14:11
 */
@Component
@Slf4j
public class McpServerHandler {
    @Resource
    ApiUrl apiUrl;
    @Resource
    RestTemplate restTemplate;
    /**
     * Get mcp-server list
     */
    private static final String MCP_SERVER_LIST = "/spark/mcp_server_list";
    /**
     * Publish information to product library
     */
    private static final String MCP_SERVER_PUBLISH = "/mcp_server/publish";
    private static final String MCP_SERVER_INFO = "/spark/mcp_server";
    /**
     * Get mcp categories
     */
    private static final String MCP_SERVER_CATEGORY = "/spark/mcp_server_category";
    private static final String MCP_USER_PARAMETERS = "/spark/mcp_user_parameters";
    private static final String MCP_SERVER_LINK_PUBLISH = "/api/v1/mcp";
    private static final String GET_MCP_URL = "/mcp/v1/shorten";
    private static final String MCP_SERVER_CALL_TOOL = "/api/v1/mcp/call_tool";
    private static final String MCP_SERVER_AUTH = "/v2/auth";

    /**
     * Get mcp tool list from Teacher Zhang
     *
     * @param categoryId Optional parameter, mcp_server category id, default query all
     * @param page
     * @param pageSize
     * @return
     */
    public List<McpServerTool> getMcpToolList(String categoryId, Integer page, Integer pageSize, String uid) {
        PageData<T> pageData = new PageData<>();

        try {
            String url = apiUrl.getMcpToolServer() + MCP_SERVER_LIST;
            if (page != null) {
                url = url + "?page=" + page;
            }
            if (pageSize != null) {
                url = url + "&page_size=" + pageSize;
            }
            if (StringUtils.isNotBlank(categoryId)) {
                url = url + "&category_id=" + categoryId;
            }
            if (uid != null) {
                url = url + "&user_id=" + uid;
            }
            log.info("getMcpToolList request url:{}", url);
            String resp = OkHttpUtil.get(url);
            JSONObject respObject = JSON.parseObject(resp);
            log.info("getMcpToolList response data:{}", resp);
            if (respObject != null && respObject.getIntValue("code") == 0) {
                pageData.setPageData(respObject.getJSONArray("data").toJavaList(T.class));
                pageData.setTotalCount(respObject.getLong("total"));
                JSONArray data = respObject.getJSONArray("data");
                List<McpServerTool> toolList = data.toJavaList(McpServerTool.class);
                return toolList;
            }
            return null;
        } catch (Exception e) {
            log.info("getMcpToolList get error");
            return null;
        }
    }

    /**
     * Get mcp categories
     *
     * @param req
     * @return
     */
    public JSONArray getMcpCategoryList(JSONObject req) {
        try {
            String url = apiUrl.getMcpToolServer() + MCP_SERVER_CATEGORY;
            log.info("getMcpToolList request url:{}\ndata:{}", url, JSON.toJSONString(req));
            String resp = OkHttpUtil.get(url);
            JSONObject respObject = JSON.parseObject(resp);
            log.info("getMcpToolList response data:{}", resp);
            if (respObject.getIntValue("code") == 0 && respObject.getInteger("total") >= 1) {
                return respObject.getJSONArray("data");
            }
            return null;
        } catch (Exception e) {
            log.info("getMcpToolList get error");
            return null;
        }
    }

    /**
     * Publish information to product library
     *
     * @param req
     * @return
     */
    public Boolean sendMcpPublish(JSONObject req) {
        try {
            String url = apiUrl.getMcpToolServer() + MCP_SERVER_PUBLISH;
            log.info("sendMcpPublish data url:{} ,  data:{}", url, JSON.toJSONString(req));
            String resp = OkHttpUtil.post(url, req.toString());
            JSONObject respObject = JSON.parseObject(resp);
            log.info("sendMcpPublish data response data:{}", resp);
            if (respObject.getIntValue("code") == 0) {
                return true;
            }
            return false;
        } catch (Exception e) {
            log.info("sendMcpPublish data error: ", e);
            return false;
        }
    }

    public JSONObject mcpPublish(JSONObject req) {
        try {
            String url = apiUrl.getToolUrl() + MCP_SERVER_LINK_PUBLISH;
            log.info("Mcp publish data url:{}\ndata:{}", url, JSON.toJSONString(req));
            String resp = OkHttpUtil.post(url, req.toString());
            JSONObject respObject = JSON.parseObject(resp);
            log.info("Mcp publish data response data:{}", resp);
            if (respObject.getIntValue("code") == 0) {
                return respObject.getJSONObject("data");
            }
            throw new BusinessException(ResponseEnum.FAILED_MCP_REG);
        } catch (Exception e) {
            log.error("Mcp publish data error", e);
            throw new BusinessException(ResponseEnum.FAILED_MCP_REG);
        }
    }

    /**
     * Debug tool
     *
     * @param req
     */
    public JSONObject debugServerTool(JSONObject req) {
        try {
            String url = apiUrl.getToolUrl() + MCP_SERVER_CALL_TOOL;
            log.info("Mcp tool call url:{}\ndata:{}", url, JSON.toJSONString(req));
            String resp = OkHttpUtil.post(url, req.toString());
            JSONObject respObject = JSON.parseObject(resp);
            log.info("Mcp tool call response data:{}", resp);
            if (respObject.getIntValue("code") == 0) {
                return respObject.getJSONObject("data");
            }
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, respObject.getString("message"));
        } catch (Exception e) {
            log.info("Mcp tool call error");
            throw new BusinessException(ResponseEnum.FAILED_TOOL_CALL);
        }
    }

    public JSONObject getMcpServerInfo(String serverId) {
        try {
            String url = apiUrl.getMcpToolServer() + MCP_SERVER_INFO + "?mcp_server_id=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8.name());

            log.info("Mcp server info url:{}\ndata:{}", url, serverId);
            String resp = OkHttpUtil.get(url);
            JSONObject respObject = JSON.parseObject(resp);
            log.info("Mcp server info response data:{}", resp);
            if (respObject.getIntValue("code") == 0) {
                return respObject.getJSONObject("data");
            }
            throw new BusinessException(ResponseEnum.FAILED_MCP_GET_DETAIL);
        } catch (Exception e) {
            log.info("Mcp server info data error");
            throw new BusinessException(ResponseEnum.FAILED_MCP_GET_DETAIL);
        }
    }

    /**
     * Check if mcp tool needs env key
     *
     * @param serverId
     * @return
     */
    public JSONObject checkMcpToolsIsNeedEnvKeys(String serverId) {
        try {
            String url = apiUrl.getMcpToolServer() + MCP_USER_PARAMETERS + "?mcp_server_id=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8.name());
            log.info("checkMcpToolsIsNeedEnvKeys data url:{}", url);
            String resp = OkHttpUtil.get(url, null);
            JSONObject respObject = JSON.parseObject(resp);
            log.info("checkMcpToolsIsNeedEnvKeys data response data:{}", resp);
            if (respObject.getIntValue("code") == 0) {
                JSONArray data = respObject.getJSONArray("data");
                String userGuide = respObject.getString("user_guide");
                for (Object datum : data) {
                    JSONObject obj = (JSONObject) datum;
                    if ("env".equals(obj.getString("type"))) {
                        obj.put("user_guide", userGuide);
                        return obj;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.info("checkMcpToolsIsNeedEnvKeys data error: ", e);
            return null;
        }
    }

    /**
     * Authorization
     *
     * @param req
     * @return
     */
    public JSONObject McpAuth(JSONObject req) {
        try {
            String url = apiUrl.getMcpAuthServer() + MCP_SERVER_AUTH;
            log.info("Mcp auth data url:{}\ndata:{}", url, JSON.toJSONString(req));
            String resp = OkHttpUtil.post(url, req.toString());
            JSONObject respObject = JSON.parseObject(resp);
            log.info("Mcp auth data response data:{}", resp);
            Integer ret = respObject.getInteger("ret");
            if (ret != null && ret == 0) {
                return respObject.getJSONObject("data");
            }
            throw new BusinessException(ResponseEnum.FAILED_AUTH);
        } catch (Exception e) {
            log.error("Mcp auth error", e);
            throw new BusinessException(ResponseEnum.FAILED_AUTH);
        }
    }

    /**
     * Generate short link
     *
     * @param req
     * @return
     */
    public String getMcpUrl(JSONObject req, String appid) {
        try {
            String url = apiUrl.getMcpUrlServer() + GET_MCP_URL;
            log.info("Mcp publish data url:{}\ndata:{}", url, JSON.toJSONString(req));
            Map<String, String> headerMap = new HashMap<>();
            headerMap.put("x-consumer-username", appid);
            String resp = OkHttpUtil.post(url, headerMap, req.toString());
            JSONObject respObject = JSON.parseObject(resp);
            log.info("Mcp publish data response data:{}", resp);
            if (respObject.getIntValue("code") == 0) {
                return respObject.getJSONObject("data").getString("url");
            }
            throw new BusinessException(ResponseEnum.FAILED_GENERATE_SERVER_URL);
        } catch (Exception e) {
            log.info("Mcp publish data error");
            throw new BusinessException(ResponseEnum.FAILED_GENERATE_SERVER_URL);

        }
    }
}
