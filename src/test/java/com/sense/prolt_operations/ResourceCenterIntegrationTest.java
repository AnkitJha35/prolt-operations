package com.sense.prolt_operations;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
