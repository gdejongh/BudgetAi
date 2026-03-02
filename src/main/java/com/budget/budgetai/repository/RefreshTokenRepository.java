package com.budget.budgetai.repository;

import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.token = :token")
    int deleteByTokenValue(@Param("token") String token);

    void deleteByAppUser(AppUser appUser);

    void deleteByExpiresAtBefore(ZonedDateTime dateTime);
}
