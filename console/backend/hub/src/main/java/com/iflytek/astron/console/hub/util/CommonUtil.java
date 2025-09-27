package com.iflytek.astron.console.hub.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * @author yingpeng
 */
public class CommonUtil {

    // Calculate how many seconds remain until the end of the day
    public static int calculateSecondsUntilEndOfDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = LocalDateTime.of(now.toLocalDate(), LocalTime.MAX);
        Duration duration = Duration.between(now, endOfDay);
        return (int) duration.getSeconds();
    }
}
