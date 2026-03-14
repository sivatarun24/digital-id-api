package com.astr.react_backend.repositroy;

import com.astr.react_backend.controller.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhoneNo(Long phoneNo);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNo(Long phoneNo);
}
