package com.restio.auth.domain;

import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import com.restio.auth.Permission;
import com.restio.shared.domain.AuditableEntity;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A named set of permissions. The six system roles cannot be deleted but their permissions can be
 * edited (stage 4.1).
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "roles")
public class Role extends AuditableEntity {

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "system", nullable = false)
    private boolean system;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);

    public Set<Permission> getPermissions() {
        return Set.copyOf(permissions);
    }
}
