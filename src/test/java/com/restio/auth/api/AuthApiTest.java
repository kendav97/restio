package com.restio.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restio.AbstractIntegrationTest;
import com.restio.auth.domain.Device;
import com.restio.auth.fixtures.AuthFixtures;

@AutoConfigureMockMvc
class AuthApiTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String DEVICE_TOKEN = "device-token-of-restaurant-1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthFixtures fixtures;

    private Device device;

    @BeforeEach
    void setUp() {
        fixtures.clean();
        fixtures.user(ADMIN_EMAIL, ADMIN_PASSWORD, "9999", "ADMIN", 1L, 2L);
        fixtures.user("waiter@test.local", null, "1234", "WAITER", 1L);
        device = fixtures.device(1L, DEVICE_TOKEN);
    }

    @Test
    @DisplayName("login correcto devuelve tokens y /me muestra permisos y locales")
    void login_validCredentials_returnsTokensUsableOnMe() throws Exception {
        JsonNode tokens = login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk()).json();

        assertThat(tokens.get("refreshToken").asText()).isNotBlank();
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.restaurantIds.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    @DisplayName("contraseña errónea devuelve 401 con código propio")
    void login_wrongPassword_returns401() throws Exception {
        login(ADMIN_EMAIL, "wrong")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName(
            "tras 5 intentos fallidos la cuenta queda bloqueada aunque la contraseña sea buena")
    void login_afterFiveFailures_isLocked() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(ADMIN_EMAIL, "wrong").andExpect(status().isUnauthorized());
        }

        login(ADMIN_EMAIL, ADMIN_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("el refresh rota el token y reutilizar el anterior revoca todas las sesiones")
    void refresh_rotatesAndDetectsReuse() throws Exception {
        String first = login(ADMIN_EMAIL, ADMIN_PASSWORD).json().get("refreshToken").asText();

        String second =
                refresh(first).andExpect(status().isOk()).json().get("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"));
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout revoca el refresh token")
    void logout_revokesRefreshToken() throws Exception {
        String refreshToken =
                login(ADMIN_EMAIL, ADMIN_PASSWORD).json().get("refreshToken").asText();

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("refreshToken", refreshToken)))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PIN desde un dispositivo registrado abre sesión de turno limitada a su local")
    void pinLogin_fromRegisteredDevice_returnsShiftToken() throws Exception {
        JsonNode tokens =
                pinLogin(device.getId(), DEVICE_TOKEN, "9999").andExpect(status().isOk()).json();

        assertThat(tokens.hasNonNull("refreshToken")).isFalse();
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(8 * 3600);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantIds.length()").value(1))
                .andExpect(jsonPath("$.restaurantIds[0]").value(1))
                .andExpect(jsonPath("$.deviceId").value(device.getId()));
    }

    @Test
    @DisplayName("PIN con token de dispositivo incorrecto devuelve 401")
    void pinLogin_wrongDeviceToken_returns401() throws Exception {
        pinLogin(device.getId(), "not-the-token", "1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_DEVICE"));
    }

    @Test
    @DisplayName("5 PIN erróneos bloquean el PIN en ese dispositivo")
    void pinLogin_afterFiveWrongPins_locksDevice() throws Exception {
        for (int i = 0; i < 5; i++) {
            pinLogin(device.getId(), DEVICE_TOKEN, "0000").andExpect(status().isUnauthorized());
        }

        pinLogin(device.getId(), DEVICE_TOKEN, "1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("sin token un endpoint protegido devuelve 401 en formato problem")
    void me_withoutToken_returns401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("un token manipulado devuelve 401")
    void me_withTamperedToken_returns401() throws Exception {
        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sin el permiso necesario se devuelve 403")
    void permissions_withoutUserManage_returns403() throws Exception {
        JsonNode waiter = pinLogin(device.getId(), DEVICE_TOKEN, "1234").json();
        JsonNode admin = login(ADMIN_EMAIL, ADMIN_PASSWORD).json();

        mockMvc.perform(
                        get("/api/v1/permissions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(waiter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/permissions").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24));
    }

    @Test
    @DisplayName("un PIN ya usado en el local se rechaza con 409")
    void changePin_duplicatedInRestaurant_returns409() throws Exception {
        JsonNode admin = login(ADMIN_EMAIL, ADMIN_PASSWORD).json();

        mockMvc.perform(
                        post("/api/v1/auth/change-pin")
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("currentSecret", ADMIN_PASSWORD, "newPin", "1234")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_PIN_IN_USE"));

        mockMvc.perform(
                        post("/api/v1/auth/change-pin")
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("currentSecret", "9999", "newPin", "4321")))
                .andExpect(status().isNoContent());
        pinLogin(device.getId(), DEVICE_TOKEN, "4321").andExpect(status().isOk());
    }

    @Test
    @DisplayName("cambiar la contraseña cierra las demás sesiones")
    void changePassword_revokesRefreshTokens() throws Exception {
        JsonNode session = login(ADMIN_EMAIL, ADMIN_PASSWORD).json();

        mockMvc.perform(
                        post("/api/v1/auth/change-password")
                                .header(HttpHeaders.AUTHORIZATION, bearer(session))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        body(
                                                "currentPassword",
                                                ADMIN_PASSWORD,
                                                "newPassword",
                                                "a-new-password")))
                .andExpect(status().isNoContent());

        refresh(session.get("refreshToken").asText()).andExpect(status().isUnauthorized());
        login(ADMIN_EMAIL, "a-new-password").andExpect(status().isOk());
    }

    private Response login(String email, String password) throws Exception {
        return new Response(
                mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("email", email, "password", password))));
    }

    private Response pinLogin(Long deviceId, String deviceToken, String pin) throws Exception {
        String json =
                objectMapper.writeValueAsString(
                        java.util.Map.of(
                                "deviceId", deviceId, "deviceToken", deviceToken, "pin", pin));
        return new Response(
                mockMvc.perform(
                        post("/api/v1/auth/pin-login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)));
    }

    private Response refresh(String refreshToken) throws Exception {
        return new Response(
                mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("refreshToken", refreshToken))));
    }

    private String body(String... keyValues) throws Exception {
        var map = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return objectMapper.writeValueAsString(map);
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
