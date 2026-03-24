package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.AccountStatus;
import com.digitalid.api.controller.models.Role;
import com.digitalid.api.controller.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhoneNo(Long phoneNo);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNo(Long phoneNo);

    List<User> findAllByOrderByCreatedAtDesc();

    List<User> findByRoleOrderByCreatedAtDesc(Role role);

    List<User> findByInstitutionIdOrderByCreatedAtDesc(Long institutionId);

    long countByRole(Role role);

    long countByAccountStatus(AccountStatus accountStatus);
}
