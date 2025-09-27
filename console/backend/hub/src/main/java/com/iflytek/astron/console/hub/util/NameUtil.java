package com.iflytek.astron.console.hub.util;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
public class NameUtil {
    public static final Pattern pattern = Pattern.compile("(https?://)?([\\w.-]+\\.)+[a-zA-Z]{2,6}");

    public NameUtil() {}

    public static String generateUniqueFileName() {
        return generateUniqueFileName("common", "common");
    }

    public static String generateUniqueFileName(String fileName) {
        Objects.requireNonNull(fileName);
        return (new DateTime()).toString("yyyy-MM-dd_") + RandomUtil.randomString(8) + "_" + fileName;
    }

    public static String generateUniqueFileName(String fileName, String businessName) {
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(businessName);
        return businessName + "_" + (new DateTime()).toString("yyyy-MM-dd_") + RandomUtil.randomString(8) + "_" + fileName;
    }
}
