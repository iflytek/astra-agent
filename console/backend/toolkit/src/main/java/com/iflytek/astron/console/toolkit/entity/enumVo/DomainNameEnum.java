package com.iflytek.astron.console.toolkit.entity.enumVo;

/**
 * @author clliu19
 * @date 2024/05/29/15:15
 */
public enum DomainNameEnum {
    GENERAL_1_5("general", "Spark 1.5"),
    GENERAL_3_0("generalv3", "Spark 3.0"),
    GENERAL_3_5("generalv3.5", "Spark 3.5");

    private final String domain;
    private final String name;

    DomainNameEnum(String domain, String name) {
        this.domain = domain;
        this.name = name;
    }

    public String getDomain() {
        return domain;
    }

    public String getName() {
        return name;
    }

    public static String getNameByDomain(String domain) {
        for (DomainNameEnum domainNameEnum : DomainNameEnum.values()) {
            if (domainNameEnum.getDomain().equals(domain)) {
                return domainNameEnum.getName();
            }
        }
        return null;
    }
}
