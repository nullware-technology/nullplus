package com.nullware.ms_auth.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.nullware.ms_auth.dtos.responses.TokenResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private static final String ISSUER = "ms-auth";
    @Value("${api.security.token.secret}")
    private String secret;

    @Value("${api.security.access.token.expiration.minutes}")
    private int ACCESS_TOKEN_EXPIRES_MINUTES;

    @Value("${api.security.refresh.token.expiration.days}")
    private int REFRESH_TOKEN_EXPIRES_DAYS;

    public TokenResponseDTO refreshAccessToken(String refreshToken) {
        try {
            log.debug("Validating refresh token");
            Algorithm algorithm = Algorithm.HMAC256(secret.getBytes());
            String email = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(refreshToken)
                    .getSubject();

            if (email == null) {
                log.warn("Refresh token is valid but email is null");
                return null;
            }

            log.info("Generating new access token for email: {}", email);
            var newAccessToken = this.generateToken(email, ACCESS_TOKEN_EXPIRES_MINUTES, "minutes");

            return new TokenResponseDTO(
                    newAccessToken,
                    refreshToken,
                    "Bearer",
                    this.getRemainingTime(newAccessToken),
                    this.getRemainingTime(refreshToken));

        } catch (JWTVerificationException exception) {
            log.error("Invalid refresh token: {}", exception.getMessage());
            return null;
        }
    }

    public long getRemainingTime(String token) {
        Instant expirationTime = this.getExpirationTime(token);
        if (expirationTime == null) {
            log.warn("Failed to get expiration time for token: {}", token);
            return -1;
        }

        Instant now = Instant.now();
        long remainingSeconds = Duration.between(now, expirationTime).getSeconds();

        if (remainingSeconds < 0) {
            log.info("Token is expired: {}", token);
            return 0;
        }

        log.info("Remaining time for token expiration: {} seconds", remainingSeconds);
        return remainingSeconds;
    }

    public TokenResponseDTO generateTokens(String email) {
        String accessToken = generateToken(email, ACCESS_TOKEN_EXPIRES_MINUTES, "minutes");
        String refreshToken = generateToken(email, REFRESH_TOKEN_EXPIRES_DAYS, "days");

        return new TokenResponseDTO(
                accessToken,
                refreshToken,
                "Bearer",
                ACCESS_TOKEN_EXPIRES_MINUTES * 60L, // em segundos
                REFRESH_TOKEN_EXPIRES_DAYS * 24L * 3600L // em segundos
        );
    }

    public String validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret.getBytes());
            return JWT.require(algorithm)
                    .withIssuer("ms-auth")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            return null;
        }
    }

    private Instant getExpirationTime(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret.getBytes());
            Date expirationDate = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token)
                    .getExpiresAt();
            return expirationDate.toInstant();
        } catch (JWTVerificationException exception) {
            log.error("Failed to get expiration time: {}", exception.getMessage());
            return null;
        }
    }

    private String generateToken(String email, int expiresIn, String unit) {
        try {
            log.debug("Generating token for email: {} with expiration in {} {}", email, expiresIn, unit);
            Algorithm algorithm = Algorithm.HMAC256(secret.getBytes());
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(email)
                    .withExpiresAt(generateExpirationDate(expiresIn, unit))
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            log.error("Error while creating token for email {}: {}", email, exception.getMessage());
            throw new JWTCreationException("Error while generating token", exception);
        }
    }

    private Instant generateExpirationDate(int amount, String unit) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.of("-03:00"));
        return switch (unit) {
            case "minutes" -> now.plusMinutes(amount).toInstant(ZoneOffset.of("-03:00"));
            case "days" -> now.plusDays(amount).toInstant(ZoneOffset.of("-03:00"));
            default -> throw new IllegalArgumentException("Invalid time unit: " + unit);
        };
    }
}
