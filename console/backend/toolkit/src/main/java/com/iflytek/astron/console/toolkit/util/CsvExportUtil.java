package com.iflytek.astron.console.toolkit.util;

import jakarta.servlet.http.HttpServletResponse;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for exporting data to CSV format.
 *
 * <p>
 * This class provides methods to export CSV content to an HTTP response and to write rows with
 * proper escaping for CSV fields.
 * </p>
 */
public class CsvExportUtil {

    /**
     * Export CSV content to {@link HttpServletResponse}.
     *
     * <p>
     * Steps:
     * </p>
     * <ul>
     * <li>Set response headers and content type to CSV with UTF-8 encoding.</li>
     * <li>Write UTF-8 BOM to prevent garbled Chinese characters in Excel.</li>
     * <li>Write header row and data rows.</li>
     * </ul>
     *
     * @param response HTTP response object
     * @param fileName file name without ".csv" suffix
     * @param headers header row
     * @param dataRows list of data rows, each row is a list of string fields
     * @throws RuntimeException if any I/O error occurs during export
     */
    public static void exportToResponse(HttpServletResponse response,
            String fileName,
            List<String> headers,
            List<List<String>> dataRows) {
        try {
            response.setContentType("text/csv; charset=UTF-8");
            String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8") + ".csv";
            response.setHeader("Content-Disposition", "attachment; filename=" + encodedFileName);
            // Write UTF-8 BOM to prevent Chinese garbled characters in Excel
            response.getOutputStream().write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8), true);

            // Write header row
            writeCsvRow(writer, headers);

            // Write data rows
            for (List<String> row : dataRows) {
                writeCsvRow(writer, row);
            }

            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException("CSV export failed", e);
        }
    }

    /**
     * Write one CSV row with proper escaping.
     *
     * <p>
     * Each field will be escaped using {@link #escapeCsv(String)}.
     * </p>
     *
     * @param writer the PrintWriter to write into
     * @param row the list of string fields for the row
     * @throws NullPointerException if {@code writer} or {@code row} is null
     */
    public static void writeCsvRow(PrintWriter writer, List<String> row) {
        String line = row.stream()
                .map(CsvExportUtil::escapeCsv)
                .collect(Collectors.joining(","));
        writer.println(line);
    }

    /**
     * Escape a CSV field according to RFC 4180.
     *
     * <p>
     * Rules:
     * </p>
     * <ul>
     * <li>If the field contains comma, double quote, or newline, enclose it in double quotes.</li>
     * <li>Escape inner double quotes by replacing them with two double quotes.</li>
     * </ul>
     *
     * @param field the original string field (nullable)
     * @return the escaped string, never null
     */
    private static String escapeCsv(String field) {
        if (field == null) {
            return "";
        }
        boolean hasSpecialChar = field.contains(",") || field.contains("\"")
                || field.contains("\n") || field.contains("\r");
        String escaped = field.replace("\"", "\"\"");
        return hasSpecialChar ? "\"" + escaped + "\"" : escaped;
    }
}
