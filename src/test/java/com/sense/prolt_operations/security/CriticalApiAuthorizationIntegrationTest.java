package com.sense.prolt_operations.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.BaseIntegrationTest;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.testutil.TestUserHelper;

@AutoConfigureMockMvc
public class CriticalApiAuthorizationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TestUserHelper.TestUser clientUser;
    private TestUserHelper.TestUser adminUser;
    private TestUserHelper.TestUser superAdminUser;

    @BeforeEach
    void setUp() {
        clientUser = createTestUser(Role.CLIENT);
        adminUser = createTestUser(Role.ADMIN);
        superAdminUser = createTestUser(Role.SUPER_ADMIN);
    }

    @AfterEach
    void tearDown() {
        if (clientUser != null) deleteTestUser(clientUser);
        if (adminUser != null) deleteTestUser(adminUser);
        if (superAdminUser != null) deleteTestUser(superAdminUser);
    }

    // =========================================================================
    // SEC-01: Resource Center Authorization Tests
    // =========================================================================

    @Test
    @DisplayName("SEC-01: Anonymous GET access to Resource Center endpoints is permitted")
    void testAnonymousGet_ResourceCenter_Allowed() throws Exception {
        mockMvc.perform(get("/api/resource-center/region-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/resource-center/discom-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("SEC-01: Anonymous mutations and deletions to Resource Center return 401 Unauthorized")
    void testAnonymousMutations_ResourceCenter_DeniedWith401() throws Exception {
        Map<String, Object> payload = Map.of(
            "stateName", "Anon State",
            "stateCode", "ANON_01"
        );

        // POST single record
        mockMvc.perform(post("/api/resource-center/region-state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());

        // POST bulk records
        mockMvc.perform(post("/api/resource-center/region-state/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"stateName\":\"Anon Bulk\",\"stateCode\":\"ANON_B\"}]"))
                .andExpect(status().isUnauthorized());

        // PUT update
        mockMvc.perform(put("/api/resource-center/region-state/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());

        // DELETE record
        mockMvc.perform(delete("/api/resource-center/region-state/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SEC-01: Authenticated CLIENT mutations and deletions to Resource Center return 403 Forbidden")
    void testClientMutations_ResourceCenter_DeniedWith403() throws Exception {
        Map<String, Object> payload = Map.of(
            "stateName", "Client State",
            "stateCode", "CLI_01"
        );

        // POST single record
        mockMvc.perform(post("/api/resource-center/region-state")
                .header("Authorization", "Bearer " + clientUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());

        // POST bulk records
        mockMvc.perform(post("/api/resource-center/region-state/bulk")
                .header("Authorization", "Bearer " + clientUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"stateName\":\"Client Bulk\",\"stateCode\":\"CLI_B\"}]"))
                .andExpect(status().isForbidden());

        // PUT update
        mockMvc.perform(put("/api/resource-center/region-state/1")
                .header("Authorization", "Bearer " + clientUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());

        // DELETE record
        mockMvc.perform(delete("/api/resource-center/region-state/1")
                .header("Authorization", "Bearer " + clientUser.getToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-01: Authenticated ADMIN can create, update, and delete Resource Center records")
    void testAdminMutations_ResourceCenter_Allowed() throws Exception {
        Map<String, Object> payload = Map.of(
            "regionalGrid", "Northern",
            "regionCode", "NR",
            "regionName", "Northern Region",
            "stateName", "Admin Test State",
            "stateCode", "ADM_RC_01",
            "stateOrUt", "state"
        );

        // POST
        MvcResult postRes = mockMvc.perform(post("/api/resource-center/region-state")
                .header("Authorization", "Bearer " + adminUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode created = objectMapper.readTree(postRes.getResponse().getContentAsString()).get("data");
        int id = created.get("id").asInt();

        try {
            // PUT
            mockMvc.perform(put("/api/resource-center/region-state/" + id)
                    .header("Authorization", "Bearer " + adminUser.getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("regionName", "Updated Admin Region"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.regionName").value("Updated Admin Region"));
        } finally {
            // DELETE
            mockMvc.perform(delete("/api/resource-center/region-state/" + id)
                    .header("Authorization", "Bearer " + adminUser.getToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Record deleted successfully"));
        }
    }

    @Test
    @DisplayName("SEC-01: Authenticated SUPER_ADMIN can create, update, and delete Resource Center records")
    void testSuperAdminMutations_ResourceCenter_Allowed() throws Exception {
        Map<String, Object> payload = Map.of(
            "regionalGrid", "Western",
            "regionCode", "WR",
            "regionName", "Western Region",
            "stateName", "SuperAdmin Test State",
            "stateCode", "SA_RC_01",
            "stateOrUt", "state"
        );

        // POST
        MvcResult postRes = mockMvc.perform(post("/api/resource-center/region-state")
                .header("Authorization", "Bearer " + superAdminUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode created = objectMapper.readTree(postRes.getResponse().getContentAsString()).get("data");
        int id = created.get("id").asInt();

        try {
            // PUT
            mockMvc.perform(put("/api/resource-center/region-state/" + id)
                    .header("Authorization", "Bearer " + superAdminUser.getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("regionName", "Updated SA Region"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.regionName").value("Updated SA Region"));
        } finally {
            // DELETE
            mockMvc.perform(delete("/api/resource-center/region-state/" + id)
                    .header("Authorization", "Bearer " + superAdminUser.getToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Record deleted successfully"));
        }
    }

    // =========================================================================
    // SEC-07: Database Subsystem Authorization Tests
    // =========================================================================

    @Test
    @DisplayName("SEC-07: Anonymous GET access to Database endpoints is permitted")
    void testAnonymousGet_Database_Allowed() throws Exception {
        mockMvc.perform(get("/api/database/demand"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/database/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/database/city-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("SEC-07: Anonymous mutations to Database endpoints return 401 Unauthorized")
    void testAnonymousMutations_Database_DeniedWith401() throws Exception {
        Map<String, Object> holidayPayload = Map.of(
            "month", "November",
            "holidayDate", "2026-11-15",
            "holidayName", "Anonymous Holiday"
        );

        Map<String, Object> cityStatePayload = Map.of(
            "cityName", "AnonCity",
            "stateName", "AnonState",
            "population", 50000,
            "latitude", 25.0,
            "longitude", 80.0
        );

        MockMultipartFile csvFile = new MockMultipartFile(
            "file", "holidays.csv", "text/csv",
            "Month,Holiday_Date,Holiday_Name,Holiday_Type,State\nNov,2026-11-20,Test,Gazetted,National".getBytes()
        );

        // POST /api/database/holidays
        mockMvc.perform(post("/api/database/holidays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(holidayPayload)))
                .andExpect(status().isUnauthorized());

        // POST /api/database/holidays/upload (multipart)
        mockMvc.perform(multipart("/api/database/holidays/upload")
                .file(csvFile))
                .andExpect(status().isUnauthorized());

        // PUT /api/database/holidays/1
        mockMvc.perform(put("/api/database/holidays/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(holidayPayload)))
                .andExpect(status().isUnauthorized());

        // PATCH /api/database/holidays/1/toggle
        mockMvc.perform(patch("/api/database/holidays/1/toggle"))
                .andExpect(status().isUnauthorized());

        // POST /api/database/city-state
        mockMvc.perform(post("/api/database/city-state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cityStatePayload)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SEC-07: Authenticated CLIENT mutations to Database endpoints return 403 Forbidden")
    void testClientMutations_Database_DeniedWith403() throws Exception {
        Map<String, Object> holidayPayload = Map.of(
            "month", "November",
            "holidayDate", "2026-11-15",
            "holidayName", "Client Holiday"
        );

        Map<String, Object> cityStatePayload = Map.of(
            "cityName", "ClientCity",
            "stateName", "ClientState",
            "population", 50000,
            "latitude", 25.0,
            "longitude", 80.0
        );

        MockMultipartFile csvFile = new MockMultipartFile(
            "file", "holidays.csv", "text/csv",
            "Month,Holiday_Date,Holiday_Name,Holiday_Type,State\nNov,2026-11-20,Test,Gazetted,National".getBytes()
        );

        // POST /api/database/holidays
        mockMvc.perform(post("/api/database/holidays")
                .header("Authorization", "Bearer " + clientUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(holidayPayload)))
                .andExpect(status().isForbidden());

        // POST /api/database/holidays/upload (multipart)
        mockMvc.perform(multipart("/api/database/holidays/upload")
                .file(csvFile)
                .header("Authorization", "Bearer " + clientUser.getToken()))
                .andExpect(status().isForbidden());

        // PUT /api/database/holidays/1
        mockMvc.perform(put("/api/database/holidays/1")
                .header("Authorization", "Bearer " + clientUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(holidayPayload)))
                .andExpect(status().isForbidden());

        // PATCH /api/database/holidays/1/toggle
        mockMvc.perform(patch("/api/database/holidays/1/toggle")
                .header("Authorization", "Bearer " + clientUser.getToken()))
                .andExpect(status().isForbidden());

        // POST /api/database/city-state
        mockMvc.perform(post("/api/database/city-state")
                .header("Authorization", "Bearer " + clientUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cityStatePayload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-07: Authenticated ADMIN can create, update, toggle, and upload holidays, and create city-state data")
    void testAdminMutations_Database_Allowed() throws Exception {
        // 1. Create Holiday
        Map<String, Object> holidayPayload = Map.of(
            "month", "December",
            "holidayDate", "2026-12-25",
            "holidayName", "Admin Holiday"
        );

        MvcResult holidayResult = mockMvc.perform(post("/api/database/holidays")
                .header("Authorization", "Bearer " + adminUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(holidayPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.holidayName").value("Admin Holiday"))
                .andReturn();

        String holidayId = objectMapper.readTree(holidayResult.getResponse().getContentAsString()).get("data").get("id").asText();

        // 2. Update Holiday
        mockMvc.perform(put("/api/database/holidays/" + holidayId)
                .header("Authorization", "Bearer " + adminUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("holidayName", "Updated Admin Holiday"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holidayName").value("Updated Admin Holiday"));

        // 3. Toggle Holiday
        mockMvc.perform(patch("/api/database/holidays/" + holidayId + "/toggle")
                .header("Authorization", "Bearer " + adminUser.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));

        // 4. Upload Holidays (CSV)
        MockMultipartFile csvFile = new MockMultipartFile(
            "file", "holidays.csv", "text/csv",
            "Month,Holiday_Date,Holiday_Name,Holiday_Type,State\nDecember,2026-12-31,New Year Eve,Gazetted,National".getBytes()
        );

        mockMvc.perform(multipart("/api/database/holidays/upload")
                .file(csvFile)
                .header("Authorization", "Bearer " + adminUser.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 5. Create City-State Entry
        Map<String, Object> cityState = Map.of(
            "cityName", "AuthAdminCity",
            "stateName", "AuthAdminState",
            "population", 123456,
            "latitude", 26.5,
            "longitude", 78.5
        );

        mockMvc.perform(post("/api/database/city-state")
                .header("Authorization", "Bearer " + adminUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cityState)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cityName").value("AuthAdminCity"));
    }

    @Test
    @DisplayName("SEC-07: Authenticated SUPER_ADMIN can create, update, toggle holidays, and create city-state data")
    void testSuperAdminMutations_Database_Allowed() throws Exception {
        // 1. Create Holiday
        Map<String, Object> holidayPayload = Map.of(
            "month", "August",
            "holidayDate", "2026-08-15",
            "holidayName", "SuperAdmin Holiday"
        );

        MvcResult holidayResult = mockMvc.perform(post("/api/database/holidays")
                .header("Authorization", "Bearer " + superAdminUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(holidayPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String holidayId = objectMapper.readTree(holidayResult.getResponse().getContentAsString()).get("data").get("id").asText();

        // 2. Toggle Holiday
        mockMvc.perform(patch("/api/database/holidays/" + holidayId + "/toggle")
                .header("Authorization", "Bearer " + superAdminUser.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));

        // 3. Create City-State Entry
        Map<String, Object> cityState = Map.of(
            "cityName", "AuthSACity",
            "stateName", "AuthSAState",
            "population", 987654,
            "latitude", 19.5,
            "longitude", 73.5
        );

        mockMvc.perform(post("/api/database/city-state")
                .header("Authorization", "Bearer " + superAdminUser.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cityState)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cityName").value("AuthSACity"));
    }
}
