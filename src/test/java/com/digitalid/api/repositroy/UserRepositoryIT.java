package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository-slice integration tests for UserRepository.
 *
 * Uses @DataJpaTest (loads only JPA layer — no web, no service beans) with
 * @AutoConfigureTestDatabase(replace = NONE) so Spring does NOT swap in H2.
 * The real MySQL container is wired via @DynamicPropertySource.
 *
 * This is lighter and faster than a full @SpringBootTest for testing
 * query methods, constraints, and ordering behavior.
 *
 * Naming: *IT.java → picked up by maven-failsafe-plugin.
 * Run with: ./mvnw verify
 */
@DataJpaTest
@Testcontainers
@ActiveProfiles("test-integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("digital_id_test")
            .withUsername("testuser")
            .withPassword("testpass")
            .withReuse(true);

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private UserRepository userRepository;

    // ── Existence checks ─────────────────────────────────────────────────────

    @Test
    void existsByUsername_shouldReturnTrueForSavedUser() {
        userRepository.save(buildUser("existsuser", "exists@example.com"));
        assertTrue(userRepository.existsByUsername("existsuser"));
    }

    @Test
    void existsByUsername_shouldReturnFalseForMissingUser() {
        assertFalse(userRepository.existsByUsername("no_such_user_xyz"));
    }

    @Test
    void existsByEmail_shouldReturnTrueForSavedUser() {
        userRepository.save(buildUser("emailuser", "emailcheck@example.com"));
        assertTrue(userRepository.existsByEmail("emailcheck@example.com"));
    }

    @Test
    void existsByPhoneNo_shouldReturnTrueForSavedUser() {
        User user = buildUser("phoneuser", "phone@example.com");
        user.setPhoneNo(5550001234L);
        userRepository.save(user);
        assertTrue(userRepository.existsByPhoneNo(5550001234L));
    }

    // ── Find by identifier ───────────────────────────────────────────────────

    @Test
    void findByUsername_shouldReturnUser() {
        userRepository.save(buildUser("findme", "findme@example.com"));
        var found = userRepository.findByUsername("findme");
        assertTrue(found.isPresent());
        assertEquals("findme@example.com", found.get().getEmail());
    }

    @Test
    void findByEmail_shouldReturnUser() {
        userRepository.save(buildUser("emailfind", "emailfind@example.com"));
        var found = userRepository.findByEmail("emailfind@example.com");
        assertTrue(found.isPresent());
        assertEquals("emailfind", found.get().getUsername());
    }

    // ── Count by role / status ───────────────────────────────────────────────

    @Test
    void countByRole_shouldReturnCorrectCount() {
        long before = userRepository.countByRole(Role.USER);
        userRepository.save(buildUser("countuser1", "count1@example.com"));
        userRepository.save(buildUser("countuser2", "count2@example.com"));
        long after = userRepository.countByRole(Role.USER);
        assertEquals(before + 2, after, "countByRole should reflect newly inserted users");
    }

    @Test
    void countByAccountStatus_shouldReturnCorrectCount() {
        long beforeInactive = userRepository.countByAccountStatus(AccountStatus.INACTIVE);
        userRepository.save(buildUser("statususer", "statususer@example.com")); // @PrePersist sets INACTIVE
        assertEquals(beforeInactive + 1, userRepository.countByAccountStatus(AccountStatus.INACTIVE));
    }

    // ── Ordering ─────────────────────────────────────────────────────────────

    @Test
    void findAllByOrderByCreatedAtDesc_shouldReturnMostRecentFirst() {
        // Insert two users; the second one should appear first in the result
        User older = buildUser("older_" + System.nanoTime(), "older@sort.com");
        User newer = buildUser("newer_" + System.nanoTime(), "newer@sort.com");
        userRepository.save(older);
        userRepository.save(newer);

        var all = userRepository.findAllByOrderByCreatedAtDesc();
        assertFalse(all.isEmpty());
        // The first result's id should be >= the second (DESC order)
        assertTrue(all.get(0).getId() >= all.get(1).getId(),
                "Most recently created user should appear first");
    }

    // ── Institution scoping ──────────────────────────────────────────────────

    @Test
    void findByInstitutionIdOrderByCreatedAtDesc_shouldReturnOnlyMembersOfThatInstitution() {
        Long institutionId = 999L; // arbitrary; no FK constraint on this test table

        User member1 = buildUser("instmember1_" + System.nanoTime(), "m1@inst.com");
        member1.setInstitutionId(institutionId);
        User member2 = buildUser("instmember2_" + System.nanoTime(), "m2@inst.com");
        member2.setInstitutionId(institutionId);
        User outsider = buildUser("outsider_" + System.nanoTime(), "out@inst.com");
        outsider.setInstitutionId(12345L);

        userRepository.save(member1);
        userRepository.save(member2);
        userRepository.save(outsider);

        var members = userRepository.findByInstitutionIdOrderByCreatedAtDesc(institutionId);
        assertTrue(members.size() >= 2, "Should return at least the 2 members saved");
        members.forEach(u ->
                assertEquals(institutionId, u.getInstitutionId(), "All returned users must belong to the institution"));
    }

    // ── DB-enforced unique constraints ───────────────────────────────────────

    @Test
    void save_duplicateUsername_shouldThrowDatabaseException() {
        userRepository.save(buildUser("dupname", "first@dup.com"));
        userRepository.flush(); // force SQL execution

        User duplicate = buildUser("dupname", "second@dup.com");
        assertThrows(Exception.class, () -> {
            userRepository.saveAndFlush(duplicate);
        }, "DB unique constraint on username must be enforced");
    }

    @Test
    void save_duplicateEmail_shouldThrowDatabaseException() {
        userRepository.save(buildUser("emaildup1", "shared@dup.com"));
        userRepository.flush();

        User duplicate = buildUser("emaildup2", "shared@dup.com");
        assertThrows(Exception.class, () -> {
            userRepository.saveAndFlush(duplicate);
        }, "DB unique constraint on email must be enforced");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User buildUser(String username, String email) {
        return User.builder()
                .username(username)
                .name("Test User")
                .email(email)
                .passwordHash("$2a$10$fakehashfortest")
                .role(Role.USER)
                // accountStatus, failedLoginAttempts, etc. set by @PrePersist
                .build();
    }
}
