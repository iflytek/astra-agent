package com.iflytek.astra.console.toolkit.handler;


public class SidManagerHandler {
    private SidManagerHandler() {}

    private static final ThreadLocal<String> LOCAL_OBJECT = new ThreadLocal<>();

    public static void set(String sid) {
        LOCAL_OBJECT.set(sid);
    }

    public static void remove() {
        LOCAL_OBJECT.remove();
    }

    public static String get() {
        return LOCAL_OBJECT.get();
    }
}
