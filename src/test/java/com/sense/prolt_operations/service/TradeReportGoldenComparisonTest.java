package com.sense.prolt_operations.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class TradeReportGoldenComparisonTest {

    private final TradeReportParser parser = new TradeReportParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private File findPdfDirectory() {
        String prop = System.getProperty("pdf.sample.dir");
        if (prop != null && !prop.isBlank()) {
            File f = new File(prop);
            if (f.exists() && f.isDirectory()) return f;
        }
        File standard = new File("/home/ankit-jha/probus-workspace/IEX-Dashboard/pdfs");
        if (standard.exists() && standard.isDirectory()) {
            return standard;
        }
        File relative = new File("../IEX-Dashboard/pdfs");
        if (relative.exists() && relative.isDirectory()) {
            return relative;
        }
        return null;
    }

    private File findPythonScript() {
        File standard = new File("/home/ankit-jha/probus-workspace/IEX-Dashboard/backend/scripts/parse_trade_report.py");
        if (standard.exists() && standard.isFile()) {
            return standard;
        }
        File relative = new File("../IEX-Dashboard/backend/scripts/parse_trade_report.py");
        if (relative.exists() && relative.isFile()) {
            return relative;
        }
        return null;
    }

    @Test
    @DisplayName("Golden comparison: Java parser vs Python script on all sample PDFs")
    void testGoldenComparisonAgainstPython() throws IOException, InterruptedException {
        File pdfDir = findPdfDirectory();
        Assumptions.assumeTrue(pdfDir != null && pdfDir.exists() && pdfDir.isDirectory(),
                "Sample PDF directory not found; skipping golden comparison test.");

        File pythonScript = findPythonScript();
        Assumptions.assumeTrue(pythonScript != null && pythonScript.exists(),
                "Python script parse_trade_report.py not found; skipping golden comparison test.");

        File[] pdfFiles = pdfDir.listFiles((dir, name) -> name.endsWith(".pdf"));
        Assumptions.assumeTrue(pdfFiles != null && pdfFiles.length > 0,
                "No PDF files found in " + pdfDir);

        Arrays.sort(pdfFiles, Comparator.comparing(File::getName));

        // Run python parser across all PDFs
        List<String> command = new java.util.ArrayList<>(List.of(
                "uv", "run", "--with", "pdfplumber", "python3",
                pythonScript.getAbsolutePath()
        ));
        for (File pdf : pdfFiles) {
            command.add(pdf.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "Failed to launch python / uv: " + e.getMessage());
            return;
        }

        String stdout = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = proc.waitFor();
        assertThat(exitCode).as("Python process failed with error: " + stderr).isEqualTo(0);

        Map<String, Object> pythonRoot = objectMapper.readValue(stdout, new TypeReference<Map<String, Object>>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> pythonData = (Map<String, Object>) pythonRoot.get("data");
        assertThat(pythonData).as("Python parsed data must not be null").isNotNull();

        for (File pdf : pdfFiles) {
            String filename = pdf.getName();
            @SuppressWarnings("unchecked")
            Map<String, Object> pyReport = (Map<String, Object>) pythonData.get(filename);
            assertThat(pyReport).as("Python output missing for " + filename).isNotNull();

            Map<String, Object> javaReport = parser.parsePdfFile(pdf);
            assertThat(javaReport).as("Java output missing for " + filename).isNotNull();

            // Assert fileKey exists and format matches <epoch millis>-<filename>
            assertThat(javaReport.get("fileKey")).as("Java report must contain fileKey").isNotNull();
            assertThat(javaReport.get("fileKey").toString()).matches("\\d+-" + filename);

            assertDeepParity(filename, pyReport, javaReport);
        }
    }

    private void assertDeepParity(String filename, Map<String, Object> expected, Map<String, Object> actual) {
        // String fields: trading_date, delivery_date, entity_id, entity_name, portfolio_code, portfolio_name, oa_market_type, remarks
        assertTrimmedStringEqual(filename, "trading_date", expected.get("trading_date"), actual.get("trading_date"));
        assertTrimmedStringEqual(filename, "delivery_date", expected.get("delivery_date"), actual.get("delivery_date"));
        assertTrimmedStringEqual(filename, "entity_id", expected.get("entity_id"), actual.get("entity_id"));
        assertTrimmedStringEqual(filename, "entity_name", expected.get("entity_name"), actual.get("entity_name"));
        assertTrimmedStringEqual(filename, "portfolio_code", expected.get("portfolio_code"), actual.get("portfolio_code"));
        assertTrimmedStringEqual(filename, "portfolio_name", expected.get("portfolio_name"), actual.get("portfolio_name"));
        assertTrimmedStringEqual(filename, "oa_market_type", expected.get("oa_market_type"), actual.get("oa_market_type"));
        assertTrimmedStringEqual(filename, "remarks", expected.get("remarks"), actual.get("remarks"));

        // Numeric totals
        assertFloatClose(filename, "funds_payin_payout", expected.get("funds_payin_payout"), actual.get("funds_payin_payout"));
        assertFloatClose(filename, "total_amount", expected.get("total_amount"), actual.get("total_amount"));
        assertFloatClose(filename, "total_trade_mwh", expected.get("total_trade_mwh"), actual.get("total_trade_mwh"));

        // Charges map
        @SuppressWarnings("unchecked")
        Map<String, Object> expCharges = (Map<String, Object>) expected.get("charges");
        @SuppressWarnings("unchecked")
        Map<String, Object> actCharges = (Map<String, Object>) actual.get("charges");
        if (expCharges != null || actCharges != null) {
            assertThat(actCharges).as(filename + ": charges map should not be null").isNotNull();
            for (Map.Entry<String, Object> entry : expCharges.entrySet()) {
                String key = entry.getKey();
                assertThat(actCharges).as(filename + ": charges key " + key).containsKey(key);
                assertFloatClose(filename, "charge " + key, entry.getValue(), actCharges.get(key));
            }
        }

        // Trades array
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expTrades = (List<Map<String, Object>>) expected.get("trades");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actTrades = (List<Map<String, Object>>) actual.get("trades");
        if (expTrades != null || actTrades != null) {
            assertThat(actTrades).as(filename + ": trades list should not be null").isNotNull();
            assertThat(actTrades).as(filename + ": trades count").hasSameSizeAs(expTrades);
            for (int i = 0; i < expTrades.size(); i++) {
                Map<String, Object> et = expTrades.get(i);
                Map<String, Object> at = actTrades.get(i);
                assertTrimmedStringEqual(filename + "[trade " + i + "]", "period", et.get("period"), at.get("period"));
                assertFloatClose(filename + "[trade " + i + "]", "qty_mw", et.get("qty_mw"), at.get("qty_mw"));
                assertFloatClose(filename + "[trade " + i + "]", "rate_mwh", et.get("rate_mwh"), at.get("rate_mwh"));
                assertFloatClose(filename + "[trade " + i + "]", "amount", et.get("amount"), at.get("amount"));
            }
        }
    }

    private void assertTrimmedStringEqual(String context, String field, Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected == null || actual == null) {
            assertThat(actual).as(context + " -> " + field).isEqualTo(expected);
            return;
        }
        String expStr = expected.toString().trim();
        String actStr = actual.toString().trim();
        assertThat(actStr).as(context + " -> " + field).isEqualTo(expStr);
    }

    private void assertFloatClose(String context, String field, Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected == null || actual == null) {
            assertThat(actual).as(context + " -> " + field).isEqualTo(expected);
            return;
        }
        double expVal = ((Number) expected).doubleValue();
        double actVal = ((Number) actual).doubleValue();
        assertThat(actVal).as(context + " -> " + field).isCloseTo(expVal, offset(1e-4));
    }
}
