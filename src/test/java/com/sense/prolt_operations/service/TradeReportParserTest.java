package com.sense.prolt_operations.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeReportParserTest {

    private TradeReportParser parser;

    @BeforeEach
    void setUp() {
        parser = new TradeReportParser();
    }

    private byte[] createPdfWithLines(List<String> lines) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.setLeading(14.0f);
                cs.newLineAtOffset(50, 700);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("CLIENT CODE / Entity ID extraction")
    void testClientCodeAndEntityId() {
        String text1 = "Entity ID : E12345\nTrading Date : 01-Aug-26";
        Map<String, Object> res1 = parser.extractReportData(text1, "test.pdf");
        assertThat(res1.get("entity_id")).isEqualTo("E12345");

        String text2 = "CLIENT CODE : CC9999\nTrading Date : 01-Aug-26";
        Map<String, Object> res2 = parser.extractReportData(text2, "test.pdf");
        assertThat(res2.get("entity_id")).isEqualTo("CC9999");
    }

    @Test
    @DisplayName("CLIENT NAME / Entity Name extraction")
    void testClientNameAndEntityName() {
        String text1 = "Entity Name : Acme Power Corp\nPortfolio Code : P100\nTrading Date : 01-Aug-26";
        Map<String, Object> res1 = parser.extractReportData(text1, "test.pdf");
        assertThat(res1.get("entity_name")).isEqualTo("Acme Power Corp");

        String text2 = "CLIENT NAME : Beta Energy Ltd\nPRODUCT : DAM\nTrading Date : 01-Aug-26";
        Map<String, Object> res2 = parser.extractReportData(text2, "test.pdf");
        assertThat(res2.get("entity_name")).isEqualTo("Beta Energy Ltd");
    }

    @Test
    @DisplayName("TRADE DATE formats (DD-MM-YYYY, DD/MM/YYYY, DD-Mon-YY)")
    void testTradeDateFormats() {
        String text1 = "Trading Date : 15-08-2026\nDelivery Date : 16-08-2026";
        Map<String, Object> res1 = parser.extractReportData(text1, "test.pdf");
        assertThat(res1.get("trading_date")).isEqualTo("15-08-2026");
        assertThat(res1.get("delivery_date")).isEqualTo("16-08-2026");

        String text2 = "TRADE DATE : 15/08/2026\nDELIVERY DATE : 16/08/2026";
        Map<String, Object> res2 = parser.extractReportData(text2, "test.pdf");
        assertThat(res2.get("trading_date")).isEqualTo("15/08/2026");
        assertThat(res2.get("delivery_date")).isEqualTo("16/08/2026");

        String text3 = "Trading Date : 3-Aug-26\nDelivery Date : 4-Aug-26";
        Map<String, Object> res3 = parser.extractReportData(text3, "test.pdf");
        assertThat(res3.get("trading_date")).isEqualTo("3-Aug-26");
        assertThat(res3.get("delivery_date")).isEqualTo("4-Aug-26");
    }

    @Test
    @DisplayName("SEGMENT / PRODUCT extraction")
    void testSegmentAndProduct() {
        String text = "Trading Date : 01-Aug-26\nSEGMENT : Physical Electricity\nPRODUCT : Day Ahead Market";
        Map<String, Object> res = parser.extractReportData(text, "test.pdf");
        assertThat(res.get("segment")).isEqualTo("Physical Electricity");
        assertThat(res.get("product")).isEqualTo("Day Ahead Market");
    }

    @Test
    @DisplayName("Charges normalization (& to and, lowercase, underscores)")
    void testChargesNormalization() {
        assertThat(TradeReportParser.normalizeKey("NLDC Scheduling & Operating Charges - Buy"))
                .isEqualTo("nldc_scheduling_and_operating_charges_buy");
        assertThat(TradeReportParser.normalizeKey("CTU Transmission Charges"))
                .isEqualTo("ctu_transmission_charges");
        assertThat(TradeReportParser.normalizeKey("A & B / C - D"))
                .isEqualTo("a_and_b_c_d");

        String text = "Trading Date : 01-Aug-26\n" +
                "> NLDC Scheduling & Operating Charges - Buy 125.50\n" +
                "> CTU Transmission Charges 1,234.50\n" +
                "Fees 50.00\n" +
                "Total 1,410.00";
        Map<String, Object> res = parser.extractReportData(text, "test.pdf");
        @SuppressWarnings("unchecked")
        Map<String, Double> charges = (Map<String, Double>) res.get("charges");
        assertThat(charges).containsEntry("nldc_scheduling_and_operating_charges_buy", 125.50);
        assertThat(charges).containsEntry("ctu_transmission_charges", 1234.50);
        assertThat(charges).containsEntry("fees", 50.00);
        assertThat(res.get("total_amount")).isEqualTo(1410.00);
    }

    @Test
    @DisplayName("Valid trades table and total trade MWh computation")
    void testValidTradesTable() {
        String text = "Trading Date : 01-Aug-26\n" +
                "00:00 - 00:15 2.0 2000.00 1000.00\n" +
                "00:15 - 00:30 4.0 2500.00 2500.00";
        Map<String, Object> res = parser.extractReportData(text, "test.pdf");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trades = (List<Map<String, Object>>) res.get("trades");
        assertThat(trades).hasSize(2);
        assertThat(trades.get(0)).containsEntry("period", "00:00 - 00:15");
        assertThat(trades.get(0)).containsEntry("qty_mw", 2.0);
        assertThat(trades.get(0)).containsEntry("rate_mwh", 2000.00);
        assertThat(trades.get(0)).containsEntry("amount", 1000.00);
        // total_trade_mwh computed as sum_qty * 0.25 = (2.0 + 4.0) * 0.25 = 1.5
        assertThat(res.get("total_trade_mwh")).isEqualTo(1.5);
    }

    @Test
    @DisplayName("Empty trades table with valid date succeeds without error")
    void testEmptyTradesTableWithDate() {
        String text = "Trading Date : 01-Aug-26\nEntity ID : E100";
        Map<String, Object> res = parser.extractReportData(text, "test.pdf");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trades = (List<Map<String, Object>>) res.get("trades");
        assertThat(trades).isEmpty();
        assertThat(res.get("trading_date")).isEqualTo("01-Aug-26");
    }

    @Test
    @DisplayName("Malformed text without dates or trades throws IllegalArgumentException")
    void testMalformedTextThrowsException() {
        String text = "Random meaningless text without any trade date or trade blocks";
        assertThatThrownBy(() -> parser.extractReportData(text, "invalid.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Could not extract any meaningful data");
    }

    @Test
    @DisplayName("parseFiles reports FAILURE in files_status when file fails parsing")
    void testParseFilesRecordsFailureStatus() {
        MockMultipartFile badFile = new MockMultipartFile(
                "files",
                "corrupted.pdf",
                "application/pdf",
                "not a valid pdf".getBytes()
        );
        Map<String, Object> response = parser.parseFiles(List.of(badFile));
        assertThat(response.get("success")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filesStatus = (List<Map<String, Object>>) response.get("files_status");
        assertThat(filesStatus).hasSize(1);
        assertThat(filesStatus.get(0).get("file_name")).isEqualTo("corrupted.pdf");
        assertThat(filesStatus.get(0).get("status")).isEqualTo("FAILURE");
        assertThat(filesStatus.get(0).get("data")).isNull();
    }

    @Test
    @DisplayName("parseFiles parses synthetic PDF successfully")
    void testParseFilesSuccess() throws IOException {
        byte[] pdfBytes = createPdfWithLines(List.of(
                "Trading Date : 01-Aug-26",
                "Delivery Date : 02-Aug-26",
                "Entity ID : E500",
                "Entity Name : Test Trader",
                "00:00 - 00:15 1.0 1500.00 375.00"
        ));
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "IEX_test_report.pdf",
                "application/pdf",
                pdfBytes
        );
        Map<String, Object> response = parser.parseFiles(List.of(file));
        assertThat(response).containsKeys("success", "data", "reports", "files_status", "summary");
        assertThat(response.get("success")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filesStatus = (List<Map<String, Object>>) response.get("files_status");
        assertThat(filesStatus).hasSize(1);
        assertThat(filesStatus.get(0).get("status")).isEqualTo("SUCCESS");
        assertThat(filesStatus.get(0).get("message")).isEqualTo("OK");
        assertThat(filesStatus.get(0).get("data")).isNotNull();
        assertThat(response.get("data")).isEqualTo(filesStatus);
        assertThat(response.get("reports")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) response.get("summary");
        assertThat(summary.get("total_files")).isEqualTo(1);
        assertThat(summary.get("successful_files")).isEqualTo(1);
        assertThat(summary.get("failed_files")).isEqualTo(0);
    }
}
