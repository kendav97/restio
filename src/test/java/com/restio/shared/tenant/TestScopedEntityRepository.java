package com.restio.shared.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestScopedEntityRepository extends JpaRepository<TestScopedEntity, Long> {}
