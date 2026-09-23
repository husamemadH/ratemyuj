package com.ratemyuj.repository;

import com.ratemyuj.domain.OtpChallenge;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpChallengeRepository extends MongoRepository<OtpChallenge, String> {

    long countByEmailAndCreatedAtAfter(String email, Instant createdAt);

    long countByRequesterIpAndCreatedAtAfter(String requesterIp, Instant createdAt);

    Optional<OtpChallenge> findFirstByEmailAndConsumedFalseAndCodeExpiresAtAfterOrderByCreatedAtDesc(
            String email, Instant codeExpiresAt);

    List<OtpChallenge> findByEmailAndConsumedFalse(String email);
}
