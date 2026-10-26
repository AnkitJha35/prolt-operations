package com.sense.prolt_operations;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
public class ResourceCenterIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("RC-01: Region State CRUD with complete field mappings")
    void testRegionStateCrud() throws Exception {
        Map<String, Object> payload = Map.of(
            "regionalGrid", "Northern",
            "regionCode", "NR",
            "regionName", "Northern Region",
            "stateName", "Test Punjab",
            "stateCode", "PB_TEST",
            "stateOrUt", "state"
        );

        MvcResult result = mockMvc.perform(post("/api/resource-center/region-state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stateName").value("Test Punjab"))
                .andExpect(jsonPath("$.data.stateCode").value("PB_TEST"))
                .andExpect(jsonPath("$.data.regionalGrid").value("Northern"))
                .andExpect(jsonPath("$.data.stateOrUt").value("state"))
                .andReturn();

        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        int id = created.get("id").asInt();

        // Update
        Map<String, Object> updatePayload = Map.of(
            "regionalGrid", "Northern-Updated",
            "stateCode", "PB_UPD"
        );
        mockMvc.perform(put("/api/resource-center/region-state/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionalGrid").value("Northern-Updated"))
                .andExpect(jsonPath("$.data.stateCode").value("PB_UPD"));

        // Delete
        mockMvc.perform(delete("/api/resource-center/region-state/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Record deleted successfully"));
    }

    @Test
    @DisplayName("RC-02: Discom List CRUD with complete field mappings")
    void testDiscomListCrud() throws Exception {
        Map<String, Object> payload = Map.of(
            "code", "TEST_DISCOM_1",
            "legalName", "Test Discom Limited",
            "stateCode", "DL",
            "discomType", "Private"
        );

        MvcResult result = mockMvc.perform(post("/api/resource-center/discom-list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("TEST_DISCOM_1"))
                .andExpect(jsonPath("$.data.legalName").value("Test Discom Limited"))
                .andExpect(jsonPath("$.data.discomType").value("Private"))
                .andReturn();

        int id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asInt();

        // Update
        mockMvc.perform(put("/api/resource-center/discom-list/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("discomType", "Government"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discomType").value("Government"));

        // Delete
        mockMvc.perform(delete("/api/resource-center/discom-list/" + id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RC-03: ISTS Charges CRUD with complete field mappings")
    void testIstsChargesCrud() throws Exception {
        Map<String, Object> payload = Map.of(
            "startDate", "2027-01-01",
            "endDate", "2027-01-31",
            "istsLossPercent", 3.45
        );

        MvcResult result = mockMvc.perform(post("/api/resource-center/ists-charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.istsLossPercent").value(3.45))
                .andReturn();

        int id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asInt();

        mockMvc.perform(put("/api/resource-center/ists-charges/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("istsLossPercent", 3.85))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.istsLossPercent").value(3.85));

        mockMvc.perform(delete("/api/resource-center/ists-charges/" + id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RC-04: IEX Fees CRUD with complete field mappings")
    void testIexFeesCrud() throws Exception {
        Map<String, Object> payload = Map.of(
            "month", 202611,
            "exchangeFees", 0.02,
            "exchangeFeesGst", 0.0036,
            "nldcApplicationFees", 5000,
            "nldcSchedulingFees", 1000,
            "sldcSchedulingFees", 2000,
            "otherFixCharges", 150
        );

        MvcResult result = mockMvc.perform(post("/api/resource-center/iex-fees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.month").value(202611))
                .andExpect(jsonPath("$.data.nldcApplicationFees").value(5000))
                .andReturn();

        int id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asInt();

        mockMvc.perform(put("/api/resource-center/iex-fees/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("exchangeFees", 0.025))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exchangeFees").value(0.025));

        mockMvc.perform(delete("/api/resource-center/iex-fees/" + id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RC-05: ProLT Margin CRUD with complete field mappings")
    void testProltMarginCrud() throws Exception {
        Map<String, Object> payload = Map.of(
            "month", 202611,
            "customerId", "CUST_TEST_01",
            "tradingMargin", 0.04,
            "tradingMarginGst", 0.0072,
            "proltMargin", 0.05,
            "proltMarginGst", 0.009
        );

        MvcResult result = mockMvc.perform(post("/api/resource-center/prolt-margin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customerId").value("CUST_TEST_01"))
                .andExpect(jsonPath("$.data.proltMargin").value(0.05))
                .andReturn();

        int id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asInt();

        mockMvc.perform(put("/api/resource-center/prolt-margin/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("tradingMargin", 0.06))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tradingMargin").value(0.06));

        mockMvc.perform(delete("/api/resource-center/prolt-margin/" + id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RC-06: State Tariff CRUD with all canonical fields")
    void testStateTariffCrud() throws Exception {
        Map<String, Object> payload = Map.ofEntries(
            Map.entry("state", "Haryana"),
            Map.entry("consumerCategory", "HT Industrial"),
            Map.entry("subCategory", "General"),
            Map.entry("supplyVoltageCategory", "11kV"),
            Map.entry("supplyVoltage", "11000"),
            Map.entry("month", 202609),
            Map.entry("todStartTime", "06:00"),
            Map.entry("todEndTime", "10:00"),
            Map.entry("baseEnergyRate", 6.85),
            Map.entry("baseEnergyUnit", "kWh"),
            Map.entry("todChargePercent", 15),
            Map.entry("energyRate", 7.88),
            Map.entry("discom", "UHBVN"),
            Map.entry("consumptionMonth", 202609)
        );

        MvcResult result = mockMvc.perform(post("/api/resource-center/state-tariff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.state").value("Haryana"))
                .andExpect(jsonPath("$.data.consumerCategory").value("HT Industrial"))
                .andExpect(jsonPath("$.data.supplyVoltageCategory").value("11kV"))
                .andExpect(jsonPath("$.data.supplyVoltage").value("11000"))
                .andExpect(jsonPath("$.data.todStartTime").value("06:00"))
                .andExpect(jsonPath("$.data.todEndTime").value("10:00"))
                .andExpect(jsonPath("$.data.baseEnergyRate").value(6.85))
                .andExpect(jsonPath("$.data.baseEnergyUnit").value("kWh"))
                .andExpect(jsonPath("$.data.todChargePercent").value(15))
                .andExpect(jsonPath("$.data.energyRate").value(7.88))
                .andExpect(jsonPath("$.data.discom").value("UHBVN"))
                .andReturn();

        int id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asInt();

        mockMvc.perform(put("/api/resource-center/state-tariff/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("energyRate", 8.10, "todChargePercent", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.energyRate").value(8.10))
                .andExpect(jsonPath("$.data.todChargePercent").value(20));

        mockMvc.perform(delete("/api/resource-center/state-tariff/" + id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RC-07: FPPA Charges CRUD with complete field mappings")
    void testFppaChargesCrud() throws Exception {
        Map<String, Object> payload = Map.of(
            "state", "Gujarat",
            "discom", "DGVCL",
            "month", 202610,
            "billingMonth", 202611,
            "fppaChargePercent", 2.35
        );

        MvcResult result = mockMvc.perform(post("/api/resource-center/fppa-charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.state").value("Gujarat"))
                .andExpect(jsonPath("$.data.discom").value("DGVCL"))
                .andExpect(jsonPath("$.data.fppaChargePercent").value(2.35))
                .andReturn();

        int id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asInt();

        mockMvc.perform(put("/api/resource-center/fppa-charges/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fppaChargePercent", -1.5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fppaChargePercent").value(-1.5));

        mockMvc.perform(delete("/api/resource-center/fppa-charges/" + id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RC-08: Not found and invalid ID format error responses")
    void testErrorResponses() throws Exception {
        // Non-existent record PUT -> 404 "Record not found"
        mockMvc.perform(put("/api/resource-center/region-state/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("regionName", "Ghost"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Record not found"));

        // Non-existent record DELETE -> 404 "Record not found"
        mockMvc.perform(delete("/api/resource-center/region-state/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Record not found"));

        // Invalid ID format (non-numeric) -> 400 "Invalid ID format"
        mockMvc.perform(delete("/api/resource-center/region-state/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid ID format"));

        // Invalid ID format (<= 0) -> 400 "Invalid ID format"
        mockMvc.perform(delete("/api/resource-center/region-state/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid ID format"));
    }

    @Test
    @DisplayName("RC-09: CTU Charges JSON serialization matches ctu_charges_rs_per_kwh property name and aliases")
    void testCtuChargesSerializationAndAliases() throws Exception {
        Map<String, Object> payload = Map.of(
            "state", "Karnataka",
            "month", 202611,
            "ctu_charges_rs_per_kwh", "₹ 0.45"
        );

        MvcResult result = mockMvc.perform(post("/api/resource-center/ctu-charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ctu_charges_rs_per_kwh").value(0.45))
                .andReturn();

        int id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asInt();

        // Verify GET also returns ctu_charges_rs_per_kwh
        mockMvc.perform(get("/api/resource-center/ctu-charges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")].ctu_charges_rs_per_kwh").value(0.45));

        // Update with alternate property "charge" and "rs." prefix
        mockMvc.perform(put("/api/resource-center/ctu-charges/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("charge", "rs. 0.52"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ctu_charges_rs_per_kwh").value(0.52));

        mockMvc.perform(delete("/api/resource-center/ctu-charges/" + id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RC-10: Numeric normalization handles currency symbols, commas, percent, and whitespace")
    void testNumericNormalization() throws Exception {
        Map<String, Object> payload = Map.of(
            "state", "Rajasthan",
            "category", "Industrial",
            "fromDate", "2027-04-01",
            "toDate", "2027-04-30",
            "crossSubsidy", "₹ 1,250.50",
            "distributionWheelingCharges", "0.45 %",
            "stuLossPercent", " 3.5% ",
            "additionalCharge", "rs. 0.15"
        );

        MvcResult result = mockMvc.perform(post("/api/resource-center/state-charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.crossSubsidy").value(1250.50))
                .andExpect(jsonPath("$.data.distributionWheelingCharges").value(0.45))
                .andExpect(jsonPath("$.data.stuLossPercent").value(3.5))
                .andExpect(jsonPath("$.data.additionalCharge").value(0.15))
                .andReturn();

        int id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asInt();

        mockMvc.perform(delete("/api/resource-center/state-charges/" + id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RC-11: Duplicate record returns HTTP 409 Conflict with standard error message")
    void testDuplicateConflictReturns409() throws Exception {
        Map<String, Object> payload1 = Map.of(
            "stateName", "Duplicate State Test",
            "stateCode", "DUP_TEST"
        );

        MvcResult result1 = mockMvc.perform(post("/api/resource-center/region-state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload1)))
                .andExpect(status().isCreated())
                .andReturn();

        int id1 = objectMapper.readTree(result1.getResponse().getContentAsString()).get("data").get("id").asInt();

        try {
            // Posting identical stateCode should trigger unique index violation -> 409 Conflict
            mockMvc.perform(post("/api/resource-center/region-state")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload1)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Record already exists (Duplicate Conflict)"));
        } finally {
            mockMvc.perform(delete("/api/resource-center/region-state/" + id1))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("RC-12: Domain validation checks required fields, month format, and numerical bounds")
    void testValidationErrors() throws Exception {
        // Missing required field -> 400 Bad Request
        mockMvc.perform(post("/api/resource-center/region-state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("regionName", "North"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error: stateName is required"));

        // Invalid month format -> 400 Bad Request
        mockMvc.perform(post("/api/resource-center/iex-fees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("month", 13, "exchangeFees", 0.02))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error: month must be between 1 and 12, or in YYYYMM format"));

        mockMvc.perform(post("/api/resource-center/iex-fees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("month", "invalid_month", "exchangeFees", 0.02))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error: month must be a number or a valid month name"));

        // Negative number check on istsLossPercent -> 400 Bad Request
        mockMvc.perform(post("/api/resource-center/ists-charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "startDate", "2027-01-01",
                    "endDate", "2027-01-31",
                    "istsLossPercent", -2.5
                ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error: istsLossPercent cannot be negative"));

        // Percentage > 100 on istsLossPercent -> 400 Bad Request
        mockMvc.perform(post("/api/resource-center/ists-charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "startDate", "2027-01-01",
                    "endDate", "2027-01-31",
                    "istsLossPercent", 105.0
                ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error: istsLossPercent cannot exceed 100"));

        // Negative fppaChargePercent is explicitly permitted -> 201 Created
        MvcResult fppaRes = mockMvc.perform(post("/api/resource-center/fppa-charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "state", "West Bengal",
                    "month", 202611,
                    "fppaChargePercent", -1.8
                ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fppaChargePercent").value(-1.8))
                .andReturn();

        int fppaId = objectMapper.readTree(fppaRes.getResponse().getContentAsString()).get("data").get("id").asInt();
        mockMvc.perform(delete("/api/resource-center/fppa-charges/" + fppaId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RC-13: Bulk upsert performs update when id is provided and create when id is omitted")
    void testBulkUpsertMixedInsertAndUpdate() throws Exception {
        // Step 1: Pre-create a record
        Map<String, Object> initialPayload = Map.of(
            "code", "DISCOM_UP1",
            "legalName", "Discom Initial",
            "stateCode", "DL"
        );

        MvcResult createRes = mockMvc.perform(post("/api/resource-center/discom-list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialPayload)))
                .andExpect(status().isCreated())
                .andReturn();

        int id1 = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("data").get("id").asInt();

        try {
            // Step 2: Post bulk payload with one update (id1) and one insert (new)
            List<Map<String, Object>> bulkPayload = List.of(
                Map.of("id", id1, "legalName", "Discom Updated In Bulk"),
                Map.of("code", "DISCOM_UP2", "legalName", "Discom New In Bulk", "stateCode", "HR")
            );

            MvcResult bulkRes = mockMvc.perform(post("/api/resource-center/discom-list/bulk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(bulkPayload)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").value(id1))
                    .andExpect(jsonPath("$.data[0].legalName").value("Discom Updated In Bulk"))
                    .andExpect(jsonPath("$.data[1].code").value("DISCOM_UP2"))
                    .andExpect(jsonPath("$.data[1].legalName").value("Discom New In Bulk"))
                    .andReturn();

            int id2 = objectMapper.readTree(bulkRes.getResponse().getContentAsString()).get("data").get(1).get("id").asInt();

            // Step 3: Verify GET reflects both the update and the insert
            mockMvc.perform(get("/api/resource-center/discom-list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.id == " + id1 + ")].legalName").value("Discom Updated In Bulk"))
                    .andExpect(jsonPath("$.data[?(@.id == " + id2 + ")].legalName").value("Discom New In Bulk"));

            // Cleanup id2
            mockMvc.perform(delete("/api/resource-center/discom-list/" + id2))
                    .andExpect(status().isOk());
        } finally {
            // Cleanup id1
            mockMvc.perform(delete("/api/resource-center/discom-list/" + id1))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("RC-14: Bulk operations execute within transaction and roll back on validation failure")
    void testBulkRollbackOnAtomicFailure() throws Exception {
        List<Map<String, Object>> failingBulkPayload = List.of(
            Map.of("code", "DISCOM_ROLLBACK", "legalName", "Discom Valid", "stateCode", "PB"),
            Map.of("code", "DISCOM_ERR", "stateCode", "DL") // Invalid: missing required legalName
        );

        mockMvc.perform(post("/api/resource-center/discom-list/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(failingBulkPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error: legalName is required"));

        // Verify that DISCOM_ROLLBACK was NOT committed to the database
        mockMvc.perform(get("/api/resource-center/discom-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'DISCOM_ROLLBACK')]").doesNotExist());
    }
}


