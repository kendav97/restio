package com.restio.shared.tenant;

import org.springframework.stereotype.Component;

/** Exposes a repository call that runs with the isolation filter disabled. */
@Component
public class SystemContextFixture {

    private final TestScopedEntityRepository repository;

    public SystemContextFixture(TestScopedEntityRepository repository) {
        this.repository = repository;
    }

    @SystemContext
    public long countAll() {
        return repository.findAll().size();
    }
}
