package com.agil.energy.repository;

import com.agil.energy.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    Optional<OtpToken> findByTokenId(String tokenId);

    @Modifying
    @Query("DELETE FROM OtpToken o WHERE o.expiresAt < :now OR o.used = true")
    int deleteExpiredOrUsed(LocalDateTime now);
}