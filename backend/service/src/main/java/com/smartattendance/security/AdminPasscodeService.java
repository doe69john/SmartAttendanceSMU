package com.smartattendance.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminPasscodeService {

    private final PasswordEncoder passwordEncoder;
    private final String passcodeHash;

    @Autowired
    public AdminPasscodeService(PasswordEncoder passwordEncoder,
                                @Value("${app.security.admin-passcode-hash:}") String passcodeHash,
                                @Value("${app.security.admin-passcode:}") String passcodePlaintext) {
        this(passwordEncoder, passcodeHash, passcodePlaintext, true);
    }

    AdminPasscodeService(PasswordEncoder passwordEncoder,
                         String passcodeHash,
                         String passcodePlaintext,
                         boolean sanitize) {
        this.passwordEncoder = passwordEncoder;
        String sanitizedHash = sanitize ? sanitize(passcodeHash) : passcodeHash;
        String sanitizedPlaintext = sanitize ? sanitize(passcodePlaintext) : passcodePlaintext;
        this.passcodeHash = resolvePasscodeHash(passwordEncoder, sanitizedHash, sanitizedPlaintext);
    }

    public boolean isPasscodeRequired() {
        return StringUtils.hasText(passcodeHash);
    }

    public boolean validatePasscode(String candidate) {
        if (!isPasscodeRequired()) {
            return true;
        }
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        return passwordEncoder.matches(candidate, passcodeHash);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed;
    }

    private static String resolvePasscodeHash(PasswordEncoder passwordEncoder, String configuredHash, String plaintext) {
        if (StringUtils.hasText(configuredHash)) {
            return configuredHash;
        }

        if (!StringUtils.hasText(plaintext)) {
            return "";
        }

        return passwordEncoder.encode(plaintext);
    }
}
