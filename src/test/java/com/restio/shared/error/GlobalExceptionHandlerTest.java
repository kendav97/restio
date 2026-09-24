package com.restio.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new FailingController())
                        .setControllerAdvice(new GlobalExceptionHandler(messages))
                        .build();
    }

    @Test
    @DisplayName("un recurso inexistente devuelve 404 con el código del error")
    void notFound_returnsProblemDetailWith404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.type").value("https://restio.app/errors/not-found"))
                .andExpect(jsonPath("$.detail").value("Table 7 does not exist"));
    }

    @Test
    @DisplayName("una regla de negocio incumplida devuelve 409 con el código del módulo")
    void businessRule_returnsProblemDetailWith409() throws Exception {
        mockMvc.perform(get("/test/business-rule"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("TABLE_NOT_FREE"))
                .andExpect(jsonPath("$.type").value("https://restio.app/errors/table-not-free"));
    }

    @Test
    @DisplayName("un cuerpo no válido devuelve 400 con la lista de campos")
    void invalidBody_returnsProblemDetailWithFieldErrors() throws Exception {
        mockMvc.perform(
                        post("/test/validated")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("un error no controlado devuelve 500 sin filtrar la traza")
    void unexpectedError_returns500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    private enum TestErrorCode implements ErrorCode {
        TABLE_NOT_FREE;

        @Override
        public String code() {
            return name();
        }
    }

    record NamedRequest(@NotBlank String name) {}

    @RestController
    static class FailingController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException("Table", 7);
        }

        @GetMapping("/test/business-rule")
        void businessRule() {
            throw new BusinessRuleException(TestErrorCode.TABLE_NOT_FREE, "Table 7 is not free");
        }

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody NamedRequest request) {}

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("internal detail that must not leak");
        }
    }
}
