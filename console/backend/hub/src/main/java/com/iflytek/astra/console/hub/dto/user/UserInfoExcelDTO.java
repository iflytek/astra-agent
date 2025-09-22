package com.iflytek.astra.console.hub.dto.user;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * Batch import for inviting users
 */
@Data
public class UserInfoExcelDTO {

    @ExcelProperty("Mobile Number")
    private String mobile;
}
