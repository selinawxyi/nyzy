package com.nyzy.imports;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 导入结果报告 */
@Data
public class ImportResult {
    private int total;
    private int success;
    private int failed;
    private List<String> errors = new ArrayList<>();
}
