package com.restio.auth.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restio.auth.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    /** Enabled users of a restaurant that have a PIN: the candidates for a PIN login. */
    @Query(
            "select distinct u from User u join u.restaurantIds r"
                    + " where r = :restaurantId and u.enabled = true and u.pinHash is not null")
    List<User> findPinUsersOfRestaurant(@Param("restaurantId") Long restaurantId);
}
