package com.nyzy.common;

/** 解析 region_path("省/市/区县/乡镇/村/组")取"村"段的公共逻辑, 被多个模块的村级统计复用. */
public final class RegionPathUtil {

    private RegionPathUtil() {
    }

    public static String village(String regionPath) {
        if (regionPath == null) return "未知";
        String[] parts = regionPath.split("/");
        // 省/市/区县/乡镇/村/组 -> 取「村」(index 4), 不足则取末段
        if (parts.length >= 5) return parts[4];
        return parts[parts.length - 1];
    }
}
