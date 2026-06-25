package com.nyzy.gis;

import com.nyzy.common.ApiException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小化 dBASE(.dbf) 属性表解析器, 配合 ShpReader 读取 Shapefile 的属性字段。
 * 国内确权数据常见 dbf 用 GBK 编码中文字段名/值, 默认按 GBK 解码。
 */
public final class DbfReader {

    private static final Charset GBK = charsetOrFallback("GBK");

    private DbfReader() {}

    public static List<Map<String, String>> read(InputStream in) {
        try {
            byte[] all = readAll(in);
            int numRecords = u32le(all, 4);
            int headerSize = u16le(all, 8);
            int recordSize = u16le(all, 10);

            List<String> fieldNames = new ArrayList<>();
            List<Integer> fieldLens = new ArrayList<>();
            int descPos = 32;
            while (descPos < headerSize - 1 && (all[descPos] & 0xFF) != 0x0D) {
                String name = new String(all, descPos, 11, GBK).trim();
                // 字段名以 0x00 结尾的部分会被 trim 掉多余空格, 但保留中文截断风险, 已知限制
                int idx = name.indexOf('\0');
                if (idx >= 0) name = name.substring(0, idx);
                int len = all[descPos + 16] & 0xFF;
                fieldNames.add(name);
                fieldLens.add(len);
                descPos += 32;
            }

            List<Map<String, String>> rows = new ArrayList<>();
            int pos = headerSize;
            for (int r = 0; r < numRecords && pos < all.length; r++) {
                int deletionFlag = all[pos] & 0xFF;
                int fieldPos = pos + 1;
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < fieldNames.size(); i++) {
                    int len = fieldLens.get(i);
                    String val = new String(all, fieldPos, len, GBK).trim();
                    row.put(fieldNames.get(i), val);
                    fieldPos += len;
                }
                if (deletionFlag != 0x2A) rows.add(row); // 0x2A = 已标记删除的记录, 跳过
                pos += recordSize;
            }
            return rows;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(".dbf 属性表解析失败: " + e.getMessage());
        }
    }

    private static int u16le(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int u32le(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static Charset charsetOrFallback(String name) {
        try { return Charset.forName(name); } catch (Exception e) { return Charset.defaultCharset(); }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
