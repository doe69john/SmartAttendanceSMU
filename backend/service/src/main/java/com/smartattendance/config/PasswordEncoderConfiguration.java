package com.smartattendance.config;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder(@Value("${app.security.password-encoder:strength-12}") String encoderSetting) {
        int strength = 12;
        if (encoderSetting != null && encoderSetting.toLowerCase(Locale.ENGLISH).startsWith("strength-")) {
            try {
                strength = Integer.parseInt(encoderSetting.substring("strength-".length()));
            } catch (NumberFormatException ignored) {
                strength = 12;
            }
        }
        return new BCryptPasswordEncoder(strength);
    }
}
