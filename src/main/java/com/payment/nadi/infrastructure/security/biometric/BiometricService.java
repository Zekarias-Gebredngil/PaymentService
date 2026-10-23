package com.payment.nadi.infrastructure.security.biometric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class BiometricService {

    private static final Logger log = LoggerFactory.getLogger(BiometricService.class);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, ChallengeRecord> activeChallenges = new ConcurrentHashMap<>();

    public record ChallengeRecord(String challenge, String identifier, Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }


    public String generateChallenge(String identifier) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String challenge = HexFormat.of().formatHex(bytes);

        activeChallenges.put(challenge, new ChallengeRecord(
                challenge,
                identifier != null ? identifier.trim() : null,
                Instant.now().plus(CHALLENGE_TTL)
        ));

        // Purge expired challenges lazily
        activeChallenges.entrySet().removeIf(e -> e.getValue().isExpired());
        return challenge;
    }

    /**
     * Consumes and verifies that a challenge exists, matches the identifier, and is unexpired.
     * Prevents cryptographic replay attacks.
     */
    public boolean consumeChallenge(String challenge, String identifier) {
        if (challenge == null || challenge.isBlank()) return false;
        ChallengeRecord record = activeChallenges.remove(challenge.trim());
        if (record == null) {
            log.warn("Biometric challenge not found or already consumed: {}", challenge);
            return false;
        }
        if (record.isExpired()) {
            log.warn("Biometric challenge expired for identifier: {}", identifier);
            return false;
        }
        if (record.identifier() != null && identifier != null) {
            if (!record.identifier().equalsIgnoreCase(identifier.trim())) {
                log.warn("Biometric challenge identifier mismatch: expected {}, received {}",
                        record.identifier(), identifier);
                return false;
            }
        }
        return true;
    }


    public boolean verifySignature(String publicKeyBase64, String challenge, String signatureBase64) {
        try {
            if (publicKeyBase64 == null || challenge == null || signatureBase64 == null) {
                return false;
            }

            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64.trim());
            byte[] sigBytes = Base64.getDecoder().decode(signatureBase64.trim());
            byte[] dataBytes = challenge.trim().getBytes(StandardCharsets.UTF_8);

            PublicKey publicKey = parsePublicKey(keyBytes);
            String algorithm = determineSignatureAlgorithm(publicKey);

            Signature signature = Signature.getInstance(algorithm);
            signature.initVerify(publicKey);
            signature.update(dataBytes);

            boolean valid = signature.verify(sigBytes);
            if (!valid) {
                log.warn("Biometric signature verification failed for algorithm: {}", algorithm);
            }
            return valid;
        } catch (Exception e) {
            log.error("Biometric signature validation error: {}", e.getMessage(), e);
            return false;
        }
    }

    private PublicKey parsePublicKey(byte[] keyBytes) throws Exception {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        try {
            // First attempt EC (standard for mobile secure enclave: TouchID, FaceID, Android Keystore)
            KeyFactory ecFactory = KeyFactory.getInstance("EC");
            return ecFactory.generatePublic(spec);
        } catch (Exception ecEx) {
            // Fallback to RSA
            KeyFactory rsaFactory = KeyFactory.getInstance("RSA");
            return rsaFactory.generatePublic(spec);
        }
    }

    private String determineSignatureAlgorithm(PublicKey publicKey) {
        String algo = publicKey.getAlgorithm();
        if ("EC".equalsIgnoreCase(algo)) {
            return "SHA256withECDSA";
        } else if ("RSA".equalsIgnoreCase(algo)) {
            return "SHA256withRSA";
        }
        return "SHA256with" + algo;
    }
}
