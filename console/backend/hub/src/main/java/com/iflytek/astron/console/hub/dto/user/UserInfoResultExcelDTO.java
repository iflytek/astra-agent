package com.iflytek.astron.console.hub.dto.user;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.OnceAbsoluteMerge;
import lombok.Data;

/**
 * Batch import validation result for inviting users
 */
@Data
@OnceAbsoluteMerge(firstRowIndex = 0, firstColumnIndex = 0, lastRowIndex = 0, lastColumnIndex = 9)
public class UserInfoResultExcelDTO {
    @ExcelProperty(value = {"Please ensure the mobile number is registered on the Astron platform, the parsing result only displays registered users. Duplicate accounts will be automatically deduplicated.", "Mobile Number"}, index = 0)
    @ColumnWidth(15)
    private String mobile;

    /**
     * @see UserInfoResultEnum
     */
    @ExcelProperty(value = {"Please ensure the mobile number is registered on the Astron platform, the parsing result only displays registered users. Duplicate accounts will be automatically deduplicated.", "Parsing Result"}, index = 2)
    @ColumnWidth(13)
    private String result;
}
