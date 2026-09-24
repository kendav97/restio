package com.restio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(RestioApplication.class);

    @Test
    @DisplayName("los módulos respetan las reglas de dependencia")
    void modules_verify_noIllegalDependencies() {
        MODULES.verify();
    }

    @Test
    @DisplayName("genera la documentación de módulos")
    void modules_document_writesDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
