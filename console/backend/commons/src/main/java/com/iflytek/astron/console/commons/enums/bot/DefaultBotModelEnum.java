package com.iflytek.astron.console.commons.enums.bot;

/**
 * @author mingsuiyongheng 默认模型枚举类
 */
public enum DefaultBotModelEnum {

    X1("星火大模型 Spark X1", "x1", "https://openres.xfyun.cn/xfyundoc/2025-09-24/e9b74fbb-c2d6-4f4a-8c07-0ea7f03ee03a/1758681839941/icon.png"),
    SPARK_4_0("星火大模型 Spark V4.0 Ultra", "spark", "https://openres.xfyun.cn/xfyundoc/2025-09-24/e9b74fbb-c2d6-4f4a-8c07-0ea7f03ee03a/1758681839941/icon.png");

    private String name;
    private String domain;
    private String icon;

    DefaultBotModelEnum(String name, String domain, String icon) {
        this.name = name;
        this.domain = domain;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public String getDomain() {
        return domain;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * 根据domain获取对应的枚举常量
     *
     * @param domain 模型域名
     * @return 对应的枚举常量，如果未找到则返回null
     */
    public static DefaultBotModelEnum getByDomain(String domain) {
        if (domain == null) {
            return null;
        }
        for (DefaultBotModelEnum model : DefaultBotModelEnum.values()) {
            if (domain.equals(model.getDomain())) {
                return model;
            }
        }
        return null;
    }
}
