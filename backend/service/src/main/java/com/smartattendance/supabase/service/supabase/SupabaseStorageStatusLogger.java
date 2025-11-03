package com.smartattendance.supabase.service.supabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
@Component
public class SupabaseStorageStatusLogger {

    private static final Logger logger = LoggerFactory.getLogger(SupabaseStorageStatusLogger.class);

    private final SupabaseStorageService storageService;

    public SupabaseStorageStatusLogger(SupabaseStorageService storageService) {
        this.storageService = storageService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStatus() {
        if (storageService.isEnabled()) {
            logger.info("Face image storage: Supabase bucket '{}' via {}",
                    storageService.getProperties().getFaceImageBucket(),
                    storageService.getStorageBaseUrl());
        } else {
            logger.warn("Face image storage disabled: {}", storageService.getDisabledReason());
        }
    }
}
