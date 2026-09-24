package com.restio.auth.api;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.restio.auth.Permission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "auth")
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    @Operation(summary = "Catalog of permissions")
    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public List<String> list() {
        return Arrays.stream(Permission.values()).map(Enum::name).toList();
    }
}
