package com.restio.auth.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restio.auth.domain.DevicePairing;

public interface DevicePairingRepository extends JpaRepository<DevicePairing, Long> {

    Optional<DevicePairing> findByCodeHash(String codeHash);
}
