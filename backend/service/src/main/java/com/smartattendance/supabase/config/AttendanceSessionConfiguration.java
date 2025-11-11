package com.smartattendance.supabase.config;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AttendanceSessionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSessionConfiguration.class);

    @Bean
    public Clock attendanceClock(@Value("${attendance.session.time-zone:UTC}") String timeZoneId) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timeZoneId);
        } catch (DateTimeException ex) {
            log.warn("Invalid attendance.session.time-zone '{}'. Falling back to UTC. Reason: {}", timeZoneId, ex.getMessage());
            zone = ZoneOffset.UTC;
        }
        return Clock.system(zone);
    }
}
