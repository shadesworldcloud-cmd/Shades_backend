package com.sunglassstore.repository;

import com.sunglassstore.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    @EntityGraph(attributePaths = {"roles"})
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdWithRoles(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.name = :name, u.phoneNumber = :phoneNumber, " +
            "u.updatedAt = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    int updateEditableProfile(@Param("userId") Long userId, @Param("name") String name,
                              @Param("phoneNumber") String phoneNumber);

    /**
     * The same update, conditional on the caller having seen the current row.
     *
     * ID *and* expected version in the WHERE clause, version incremented in the same statement, so
     * the check and the write are one atomic operation — two concurrent editors cannot both match
     * the same version. An affected-row count of 0 means either the row is gone or somebody else
     * committed first; the service distinguishes those before choosing 404 or 409.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.name = :name, u.phoneNumber = :phoneNumber, u.version = u.version + 1, "
            + "u.updatedAt = CURRENT_TIMESTAMP WHERE u.userId = :userId AND u.version = :expectedVersion")
    int updateEditableProfileIfVersionMatches(@Param("userId") Long userId, @Param("name") String name,
                                              @Param("phoneNumber") String phoneNumber,
                                              @Param("expectedVersion") Long expectedVersion);

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles"})
    @Query(value = "SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.roleName = 'CUSTOMER' " +
            "AND NOT EXISTS (SELECT 1 FROM User u2 JOIN u2.roles ar WHERE u2 = u AND ar.roleName = 'ADMIN')",
            countQuery = "SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r.roleName = 'CUSTOMER' " +
                    "AND NOT EXISTS (SELECT 1 FROM User u2 JOIN u2.roles ar WHERE u2 = u AND ar.roleName = 'ADMIN')")
    Page<User> findCustomers(Pageable pageable);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.roleName IN :roleNames AND u.isActive = true")
    List<User> findActiveUsersByRoleNames(@Param("roleNames") java.util.Collection<String> roleNames);
}
