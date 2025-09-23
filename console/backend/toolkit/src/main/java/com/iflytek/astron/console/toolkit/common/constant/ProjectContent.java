package com.iflytek.astron.console.toolkit.common.constant;

import com.iflytek.astron.console.toolkit.config.properties.BizConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ProjectContent {

    /**
     * Business configuration instance for accessing RAG source compatibility settings
     */
    private static BizConfig bizConfig;

    /**
     * Setter method for dependency injection of BizConfig. This method is used by Spring to inject the
     * BizConfig instance and make it available for static methods to access RAG source compatibility
     * configurations.
     *
     * @param bizConfig the business configuration instance containing RAG compatibility settings
     */
    @Autowired
    public void setBizConfig(BizConfig bizConfig) {
        ProjectContent.bizConfig = bizConfig;
    }

    public static final Integer REPO_STATUS_CREATED = 1;
    public static final String REPO_OPERATE_CREATED = "create_repo";
    public static final Integer REPO_STATUS_PUBLISHED = 2;
    public static final String REPO_OPERATE_PUBLISHED = "publish_repo";
    public static final Integer REPO_STATUS_UNPUBLISHED = 3;
    public static final String REPO_OPERATE_UNPUBLISHED = "unpublish_repo";
    public static final Integer REPO_STATUS_DELETE = 4;
    public static final String REPO_OPERATE_DELETE = "delete_repo";

    public static final String HTML_FILE_TYPE = "html";
    public static final String WORD_FILE_TYPE = "doc";
    public static final String WORDX_FILE_TYPE = "docx";
    public static final String PDF_FILE_TYPE = "pdf";
    public static final String MD_FILE_TYPE = "md";
    public static final String TXT_FILE_TYPE = "txt";
    public static final String XLS_FILE_TYPE = "xls";
    public static final String XLSX_FILE_TYPE = "xlsx";
    public static final String CSV_FILE_TYPE = "csv";
    public static final String PPT_FILE_TYPE = "ppt";
    public static final String PPTX_FILE_TYPE = "pptx";
    public static final String JPG_FILE_TYPE = "jpg";
    public static final String JPEG_FILE_TYPE = "jpeg";
    public static final String PNG_FILE_TYPE = "png";
    public static final String BMP_FILE_TYPE = "bmp";
    public static final Set<String> SUPPORTED_FILE_TYPES = new HashSet<>();
    static {
        // Initialize supported file types
        SUPPORTED_FILE_TYPES.add(HTML_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(WORD_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(WORDX_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(PDF_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(MD_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(TXT_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(XLS_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(XLSX_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(CSV_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(PPT_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(PPTX_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(JPG_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(JPEG_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(PNG_FILE_TYPE);
        SUPPORTED_FILE_TYPES.add(BMP_FILE_TYPE);
    }


    public static final Integer FILE_UPLOAD_STATUS = -1;
    public static final Integer FILE_PARSE_DOING = 0;
    public static final Integer FILE_PARSE_FAILED = 1;
    public static final Integer FILE_PARSE_SUCCESSED = 2;
    // Embedding in progress
    public static final Integer FILE_EMBEDDING_DOING = 3;
    public static final Integer FILE_EMBEDDING_FAILED = 4;
    public static final Integer FILE_EMBEDDING_SUCCESSED = 5;

    // New and legacy knowledge base
    public static final String FILE_SOURCE_AIUI_RAG2_STR = "AIUI-RAG2";;
    public static final String FILE_SOURCE_CBG_RAG_STR = "CBG-RAG";
    public static final String FILE_SOURCE_RAG_FLOW_RAG_STR = "Ragflow-RAG";
    public static final String FILE_SOURCE_SPARK_RAG_STR = "SparkDesk-RAG";


    // Custom user token for launching evaluation service
    public static final String SPECIAL_COOKIE_TOKEN = "c9b1d3f0-7c62-4a8d-b5e3-9a7f6c1d2e8a";

    private static final Set<String> VALID_FILE_TYPES = new HashSet<>(Arrays.asList(
            HTML_FILE_TYPE,
            WORD_FILE_TYPE,
            WORDX_FILE_TYPE,
            PDF_FILE_TYPE,
            MD_FILE_TYPE,
            TXT_FILE_TYPE,
            XLS_FILE_TYPE,
            XLSX_FILE_TYPE,
            CSV_FILE_TYPE,
            PPT_FILE_TYPE,
            PPTX_FILE_TYPE,
            JPG_FILE_TYPE,
            JPEG_FILE_TYPE,
            PNG_FILE_TYPE,
            BMP_FILE_TYPE));

    public static boolean isValidFileType(String fileFormat) {
        return VALID_FILE_TYPES.contains(fileFormat.toLowerCase());
    }

    /**
     * Check if the source is CBG RAG compatible (includes CBG-RAG and Ragflow-RAG)
     *
     * @param source the source string to check
     * @return true if the source is CBG RAG compatible
     */
    public static boolean isCbgRagCompatible(String source) {
        if (bizConfig == null || bizConfig.getCbgRagCompatibleSources() == null) {
            // Fallback to original logic if config is not available
            return FILE_SOURCE_CBG_RAG_STR.equals(source);
        }
        return bizConfig.getCbgRagCompatibleSources().contains(source);
    }

    /**
     * Check if the source is AIUI RAG compatible by comparing against configured compatible sources.
     * This method supports flexible configuration of AIUI RAG compatible source types through
     * application properties. If configuration is not available, it falls back to the original logic
     * using FILE_SOURCE_AIUI_RAG2_STR.
     *
     * @param source the source string to check for AIUI RAG compatibility, must not be null
     * @return true if the source is AIUI RAG compatible, false otherwise
     */
    public static boolean isAiuiRagCompatible(String source) {
        if (bizConfig == null || bizConfig.getAiuiRagCompatibleSources() == null) {
            // Fallback to original logic if config is not available
            return FILE_SOURCE_AIUI_RAG2_STR.equals(source);
        }
        return bizConfig.getAiuiRagCompatibleSources().contains(source);
    }

    /**
     * Check if the source is Spark RAG compatible by comparing against configured compatible sources.
     * This method supports flexible configuration of Spark RAG compatible source types through
     * application properties. If configuration is not available, it falls back to the original logic
     * using FILE_SOURCE_SPARK_RAG_STR.
     *
     * @param source the source string to check for Spark RAG compatibility, must not be null
     * @return true if the source is Spark RAG compatible, false otherwise
     */
    public static boolean isSparkRagCompatible(String source) {
        if (bizConfig == null || bizConfig.getSparkRagCompatibleSources() == null) {
            // Fallback to original logic if config is not available
            return FILE_SOURCE_SPARK_RAG_STR.equals(source);
        }
        return bizConfig.getSparkRagCompatibleSources().contains(source);
    }
}
