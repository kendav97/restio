package com.restio.sync.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restio.AbstractIntegrationTest;
import com.restio.auth.fixtures.AuthFixtures;

/** Stage 0.5 end to end: link a device with a pairing code, open a device session, sync. */
@AutoConfigureMockMvc
class DeviceSyncApiTest extends AbstractIntegrationTest {

    private static final String MANAGER_EMAIL = "manager@test.local";
    private static final String PASSWORD = "manager-password";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthFixtures fixtures;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        fixtures.clean();
        fixtures.user(MANAGER_EMAIL, PASSWORD, "9999", "MANAGER", 1L);
        fixtures.user("waiter@test.local", PASSWORD, "1234", "WAITER", 1L);
    }

    @Test
    @DisplayName("un encargado genera un código y el dispositivo se vincula una sola vez")
    void pair_withFreshCode_linksDeviceOnce() throws Exception {
        String code = createPairing(userToken(MANAGER_EMAIL), 1L).get("code").asText();

        JsonNode device = pair(code).andExpect(status().isCreated()).json();

        assertThat(device.get("deviceCode").asText()).isEqualTo("D01");
        assertThat(device.get("restaurantId").asLong()).isEqualTo(1L);
        assertThat(device.get("deviceToken").asText()).isNotBlank();
        UUID.fromString(device.get("deviceUuid").asText());

        pair(code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_PAIRING_CODE"));
    }

    @Test
    @DisplayName("el código se acepta en minúsculas y sin guion")
    void pair_codeTypedByHand_isNormalized() throws Exception {
        String code = createPairing(userToken(MANAGER_EMAIL), 1L).get("code").asText();

        pair(code.replace("-", "").toLowerCase()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("cada dispositivo recibe un código nuevo aunque otro se haya dado de baja")
    void pair_afterDeactivation_neverReusesCode() throws Exception {
        String manager = userToken(MANAGER_EMAIL);
        JsonNode first = pairNew(manager);
        mockMvc.perform(
                        post(
                                        "/api/v1/restaurants/1/devices/{id}/deactivate",
                                        first.get("deviceId").asLong())
                                .header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        JsonNode second = pairNew(manager);

        assertThat(second.get("deviceCode").asText()).isEqualTo("D02");
        deviceLogin(first).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sin DEVICE_MANAGE o en otro local no se generan códigos")
    void createPairing_withoutPermissionOrRestaurant_returns403() throws Exception {
        perform(
                        post("/api/v1/restaurants/1/devices/pairings"),
                        userToken("waiter@test.local"),
                        Map.of("deviceName", "TPV", "deviceType", "POS"))
                .andExpect(status().isForbidden());
        perform(
                        post("/api/v1/restaurants/2/devices/pairings"),
                        userToken(MANAGER_EMAIL),
                        Map.of("deviceName", "TPV", "deviceType", "POS"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("los eventos suben una vez: reenviarlos es DUPLICATE y los de otro local REJECTED")
    void uploadEvents_isIdempotentByEventId() throws Exception {
        JsonNode device = pairNew(userToken(MANAGER_EMAIL));
        String session = bearer(deviceLogin(device).andExpect(status().isOk()).json());
        Map<String, Object> event = event(device, 1L);
        Map<String, Object> foreign = event(device, 2L);

        JsonNode first = upload(session, List.of(event, foreign)).andExpect(status().isOk()).json();
        JsonNode again = upload(session, List.of(event)).andExpect(status().isOk()).json();

        assertThat(first.at("/results/0/status").asText()).isEqualTo("APPLIED");
        assertThat(first.at("/results/1/status").asText()).isEqualTo("REJECTED");
        assertThat(again.at("/results/0/status").asText()).isEqualTo("DUPLICATE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_events", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT payload->>'tableId' FROM sync_events", String.class))
                .isEqualTo("T1");
    }

    @Test
    @DisplayName("un evento sin eventId se rechaza con 400")
    void uploadEvents_invalidEvent_returns400() throws Exception {
        JsonNode device = pairNew(userToken(MANAGER_EMAIL));
        String session = bearer(deviceLogin(device).json());
        Map<String, Object> event = event(device, 1L);
        event.remove("eventId");

        upload(session, List.of(event))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("solo las sesiones de dispositivo sincronizan; un usuario recibe 403")
    void sync_withUserSession_returns403() throws Exception {
        String manager = userToken(MANAGER_EMAIL);
        JsonNode device = pairNew(manager);

        upload(manager, List.of(event(device, 1L))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la instantánea trae los usuarios con PIN del local, con hash y permisos")
    void snapshot_returnsPinUsersOfRestaurant() throws Exception {
        JsonNode device = pairNew(userToken(MANAGER_EMAIL));
        String session = bearer(deviceLogin(device).json());

        mockMvc.perform(
                        get("/api/v1/restaurants/1/sync/snapshot")
                                .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].pinHash").isNotEmpty())
                .andExpect(jsonPath("$.users[0].permissions").isArray());
    }

    @Test
    @DisplayName("una sesión de dispositivo no puede usar endpoints de usuario")
    void me_withDeviceSession_returns403() throws Exception {
        JsonNode device = pairNew(userToken(MANAGER_EMAIL));
        String session = bearer(deviceLogin(device).json());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isForbidden());
    }

    private String userToken(String email) throws Exception {
        return bearer(
                perform(
                                post("/api/v1/auth/login"),
                                null,
                                Map.of("email", email, "password", PASSWORD))
                        .andExpect(status().isOk())
                        .json());
    }

    private JsonNode createPairing(String token, Long restaurantId) throws Exception {
        return perform(
                        post("/api/v1/restaurants/{rid}/devices/pairings", restaurantId),
                        token,
                        Map.of("deviceName", "TPV Barra", "deviceType", "POS"))
                .andExpect(status().isCreated())
                .json();
    }

    private Response pair(String code) throws Exception {
        return perform(post("/api/v1/devices/pair"), null, Map.of("code", code));
    }

    private JsonNode pairNew(String managerToken) throws Exception {
        return pair(createPairing(managerToken, 1L).get("code").asText())
                .andExpect(status().isCreated())
                .json();
    }

    private Response deviceLogin(JsonNode device) throws Exception {
        return perform(
                post("/api/v1/auth/device-login"),
                null,
                Map.of(
                        "deviceId", device.get("deviceId").asLong(),
                        "deviceToken", device.get("deviceToken").asText()));
    }

    private Response upload(String token, List<Map<String, Object>> events) throws Exception {
        return perform(post("/api/v1/restaurants/1/sync/events"), token, Map.of("events", events));
    }

    private static Map<String, Object> event(JsonNode device, Long restaurantId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("type", "TableOpened");
        event.put("aggregateId", UUID.randomUUID().toString());
        event.put("restaurantId", restaurantId);
        event.put("deviceId", device.get("deviceUuid").asText());
        event.put("userId", null);
        event.put("schemaVersion", 1);
        event.put("payload", Map.of("tableId", "T1"));
        event.put("hlc", "000001900000000:0000:" + device.get("deviceUuid").asText());
        event.put("epoch", 0);
        event.put("seq", 1);
        event.put("createdAt", "2026-09-24T10:00:00Z");
        return event;
    }

    private Response perform(MockHttpServletRequestBuilder request, String token, Object body)
            throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, token);
        }
        return new Response(
                mockMvc.perform(
                        request.contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))));
    }

    private static String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asText();
    }

    /** Small wrapper to chain expectations and then read the JSON body. */
    private final class Response {
        private final ResultActions actions;

        Response(ResultActions actions) {
            this.actions = actions;
        }

        Response andExpect(org.springframework.test.web.servlet.ResultMatcher matcher)
                throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        JsonNode json() throws Exception {
            return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }
}
