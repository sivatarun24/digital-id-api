package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.TeacherCredentialDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeacherCredentialDetailsRepository extends JpaRepository<TeacherCredentialDetails, Long> {
    Optional<TeacherCredentialDetails> findByUserCredentialId(Long userCredentialId);
    void deleteByUserCredentialId(Long userCredentialId);
}
