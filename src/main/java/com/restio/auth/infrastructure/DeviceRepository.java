package com.restio.auth.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restio.auth.domain.Device;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    List<Device> findByRestaurantIdOrderByCode(Long restaurantId);

    /** Every device ever linked to the restaurant, active or not: codes are never reused. */
    @Query("select count(d) from Device d where d.restaurantId = :restaurantId")
    long countEverLinked(@Param("restaurantId") Long restaurantId);
}
