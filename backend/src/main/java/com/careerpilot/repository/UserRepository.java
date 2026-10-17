package com.careerpilot.repository;
import com.careerpilot.entity.UserAccount;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAccount u set u.passwordHash = :passwordHash where u.id = :userId")
    int updatePasswordHash(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);
}

