package com.nyzy.common;

import com.alibaba.excel.EasyExcel;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Excel 动态导出工具 (无需为实体加注解) */
public final class ExcelUtil {

    private ExcelUtil() {}

    public static void write(HttpServletResponse response, String fileName,
                             List<String> headers, List<List<Object>> rows) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fn = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fn + ".xlsx");

            List<List<String>> head = new ArrayList<>();
            for (String h : headers) {
                List<String> col = new ArrayList<>();
                col.add(h);
                head.add(col);
            }
            EasyExcel.write(response.getOutputStream()).head(head).sheet("数据").doWrite(rows);
        } catch (IOException e) {
            throw new ApiException("导出失败: " + e.getMessage());
        }
    }
}
