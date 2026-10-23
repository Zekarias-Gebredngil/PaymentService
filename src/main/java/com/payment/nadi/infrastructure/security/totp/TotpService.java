package com.payment.nadi.infrastructure.security.totp;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;


@Service
public class TotpService {

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int MODULUS = 1_000_000;
    private static final String HMAC_ALGO = "HmacSHA1";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        byte[] buffer = new byte[20]; // 160-bit standard secret
        secureRandom.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    public List<String> generateBackupCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) {
                sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
                if (j == 3) sb.append("-");
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    public String generateOtpAuthUri(String accountName, String secret, String issuer) {
        String cleanIssuer = issuer != null && !issuer.isBlank() ? issuer.trim() : "NadiPayment";
        String cleanAccount = accountName != null ? accountName.trim() : "user";
        String encodedIssuer = URLEncoder.encode(cleanIssuer, StandardCharsets.UTF_8);
        String encodedAccount = URLEncoder.encode(cleanIssuer + ":" + cleanAccount, StandardCharsets.UTF_8);
        return String.format("otpauth://totp/%s?secret=%s&issuer=%s&digits=%d&period=%d",
                encodedAccount, secret, encodedIssuer, CODE_DIGITS, TIME_STEP_SECONDS);
    }

    public String generateQrCodeUrl(String otpAuthUri) {
        String encoded = URLEncoder.encode(otpAuthUri, StandardCharsets.UTF_8);
        return "https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=" + encoded;
    }

    public boolean verifyCode(String secret, String inputCode) {
        if (secret == null || inputCode == null || inputCode.trim().length() != CODE_DIGITS) {
            return false;
        }

        String cleanCode = inputCode.trim();
        long currentWindow = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;

        // Check clock drift tolerance window: [T-1, T, T+1]
        for (long window = currentWindow - 1; window <= currentWindow + 1; window++) {
            String expected = generateCodeForWindow(secret, window);
            if (expected.equals(cleanCode)) {
                return true;
            }
        }
        return false;
    }

    public String generateCodeForWindow(String secret, long window) {
        byte[] keyBytes = decodeBase32(secret);
        byte[] data = ByteBuffer.allocate(8).putLong(window).array();

        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGO));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % MODULUS;
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate TOTP code", e);
        }
    }

    private String encodeBase32(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int next = 0;
        int bitsLeft = 0;
        while (next < data.length) {
            buffer = (buffer << 8) | (data[next++] & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                result.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return result.toString();
    }

    private byte[] decodeBase32(String base32) {
        String clean = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        int numBytes = clean.length() * 5 / 8;
        byte[] result = new byte[numBytes];
        int buffer = 0;
        int bitsLeft = 0;
        int byteIndex = 0;

        for (int i = 0; i < clean.length(); i++) {
            int val = BASE32_CHARS.indexOf(clean.charAt(i));
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                if (byteIndex < result.length) {
                    result[byteIndex++] = (byte) ((buffer >> bitsLeft) & 0xFF);
                }
            }
        }
        return result;
    }
}
