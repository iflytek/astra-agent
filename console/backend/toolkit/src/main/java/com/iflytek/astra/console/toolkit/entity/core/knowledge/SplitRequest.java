package com.iflytek.astra.console.toolkit.entity.core.knowledge;

import com.iflytek.astra.console.toolkit.common.constant.ProjectContent;
import lombok.Data;

import java.util.List;

@Data
public class SplitRequest {
    /**
     * Required: Yes. Document address to be parsed. Document types supported: AIUI-RAG2: pdf, docx,
     * doc, txt, md, image, html (users need to download and store html to file server), url (web
     * crawler address, requires continuous yuduan2 crawler search permission) CBG-RAG: Currently
     * supports word, pdf, md, txt formats, single file size not exceeding 20MB, not exceeding 1M
     * characters. Required
     */
    String file;

    /**
     * Required: No. Chunk length range: AIUI-RAG2: Maximum not exceeding 1024, default: [16, 256]
     * CBG-RAG: default: [256, 2000]
     */
    List<Integer> lengthRange;

    /**
     * Required: No. Chunk overlap length when force splitting, default: 16
     */
    Integer overlap;

    /**
     * Required: No. AIUI-RAG2 default: [".", "!", ";", "?"] CBG-RAG default: ["/n"]
     */
    List<String> cutOff;

    @Deprecated
    List<String> separator;

    /**
     * Required: No. Whether to split by title, default split by title, false for not splitting by title
     */
    Boolean titleSplit;

    /**
     * Required: Yes. Enum AIUI-RAG2, CBG_RAG
     */
    String ragType;

    /**
     * File type 1: url
     */
    Integer resourceType;

    // Default to AIUI value
    public SplitRequest() {
        this.ragType = ProjectContent.FILE_SOURCE_AIUI_RAG2_STR;
        // this.lengthRange = Arrays.asList(16, 1024); // Set default value to [16, 1024]
        // this.separator = Arrays.asList("。", "！", "；", "？"); // Set default value to ["。", "！", "；", "？"]
    }
}
