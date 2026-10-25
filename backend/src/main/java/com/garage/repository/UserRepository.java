package com.garage.repository;

import com.garage.domain.Role;
import com.garage.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @Query("select u from User u left join fetch u.client where u.id = :id")
    Optional<User> findWithClientById(@Param("id") UUID id);

    @Query("select u from User u left join fetch u.client where lower(u.email) = lower(:email)")
    Optional<User> findWithClientByEmail(@Param("email") String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    boolean existsByClientId(UUID clientId);
}
