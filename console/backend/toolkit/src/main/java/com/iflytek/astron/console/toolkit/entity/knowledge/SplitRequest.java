package com.iflytek.astron.console.toolkit.entity.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class SplitRequest {
    /**
     * Required: Yes. Document address to parse. Supported document types: pdf, docx, doc, txt, md,
     * image, html (requires user to download and store html to file server), url (web crawler address,
     * requires continuous yuduan2 crawler search permission)
     */
    String file;

    /**
     * Required: No. Slice length range, maximum not exceeding 1024, default: [16, 256]
     */
    List<Integer> lengthRange;

    /**
     * Required: No. Slice overlap length when force cutting, default: 16
     */
    Integer overlap;

    /**
     * Required: No. Separator list, default: ["。","！","；","？"]
     */
    List<String> cutOff;

    @Deprecated
    List<String> separator;

    /**
     * Required: No. Whether to split by title, default is to split by title, false means not to split
     * by title
     */
    Boolean titleSplit;

    /**
     * Required: Yes. Enum AIUI-RAG2
     */
    String ragType = "AIUI-RAG2";
}
