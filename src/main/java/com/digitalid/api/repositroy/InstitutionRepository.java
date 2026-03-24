package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstitutionRepository extends JpaRepository<Institution, Long> {
    List<Institution> findAllByOrderByCreatedAtDesc();
    Optional<Institution> findByCode(String code);
    boolean existsByName(String name);
    boolean existsByCode(String code);
}
