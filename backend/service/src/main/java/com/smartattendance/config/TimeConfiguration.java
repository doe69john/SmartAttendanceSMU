package com.smartattendance.config;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TimeConfiguration.class);

    @Bean
    public Clock attendanceClock(@Value("${attendance.session.time-zone:Asia/Singapore}") String timeZoneId) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timeZoneId);
        } catch (DateTimeException ex) {
            ZoneId fallback = ZoneId.systemDefault();
            log.warn("Invalid attendance.session.time-zone '{}'. Falling back to system default {}.", timeZoneId, fallback);
            zone = fallback;
        }
        return Clock.system(zone);
    }
}
