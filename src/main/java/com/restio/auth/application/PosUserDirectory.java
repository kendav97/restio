package com.restio.auth.application;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.restio.auth.PosUsers;
import com.restio.auth.infrastructure.UserRepository;

@Component
class PosUserDirectory implements PosUsers {

    private final UserRepository users;

    PosUserDirectory(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PosUser> ofRestaurant(Long restaurantId) {
        return users.findPinUsersOfRestaurant(restaurantId).stream()
                .map(
                        user ->
                                new PosUser(
                                        user.getId(),
                                        user.getDisplayName(),
                                        user.getPinHash(),
                                        user.roleNames(),
                                        user.permissions().stream()
                                                .map(Enum::name)
                                                .collect(Collectors.toUnmodifiableSet())))
                .sorted(Comparator.comparing(PosUser::id))
                .toList();
    }
}
