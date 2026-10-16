package com.gatehouse.gateway.security;

import com.gatehouse.gateway.config.GatehouseProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final GatehouseProperties properties;

    public JwtService(GatehouseProperties properties) {
        this.properties = properties;
    }

    private PublicKey publicKey;
    private PrivateKey privateKey;

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    @PostConstruct
    public void init() {
        String pem = properties.getSecurity().getJwt().getPublicKeyPem();
        if (pem != null && !pem.isBlank()) {
            try {
                this.publicKey = parsePublicKeyFromPem(pem);
                log.info("Initialized JWT RS256 validator with configured public key.");
            } catch (Exception e) {
                log.error("Failed to parse provided JWT public key PEM, generating fallback keypair: {}", e.getMessage());
                generateInternalKeyPair();
            }
        } else {
            log.info("No external JWT public key configured. Generating 2048-bit RSA keypair for token verification & testing.");
            generateInternalKeyPair();
        }
    }

    private void generateInternalKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair pair = keyGen.generateKeyPair();
            this.publicKey = pair.getPublic();
            this.privateKey = pair.getPrivate();
            log.info("Internal RSA 2048-bit keypair generated successfully.");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not supported by JVM", e);
        }
    }

    private PublicKey parsePublicKeyFromPem(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    }

    /**
     * Validates RS256 token and checks signature, exp, iss, and aud.
     */
    public Claims validateToken(String token) throws JwtException {
        var jwtProps = properties.getSecurity().getJwt();
        return Jwts.parser()
                .verifyWith(this.publicKey)
                .requireIssuer(jwtProps.getIssuer())
                .requireAudience(jwtProps.getAudience())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Helper to generate a valid RS256 token (used in tests and admin onboarding demo).
     */
    public String generateToken(String subject, String clientId, List<String> scopes, long validityMinutes) {
        if (privateKey == null) {
            throw new IllegalStateException("Private key not available for token generation.");
        }
        var jwtProps = properties.getSecurity().getJwt();
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("clientId", clientId)
                .claim("scopes", scopes)
                .issuer(jwtProps.getIssuer())
                .audience().add(jwtProps.getAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validityMinutes, ChronoUnit.MINUTES)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}
