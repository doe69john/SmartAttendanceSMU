package com.smartattendance.companion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompanionRuntime {

    private static final Logger logger = LoggerFactory.getLogger(CompanionRuntime.class);

    private CompanionRuntime() {
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static void launch(String[] args) {
        try {
            CompanionSettings settings = CompanionSettings.load();
            CompanionSessionManager sessionManager = new CompanionSessionManager(settings);
            CompanionHttpServer server = new CompanionHttpServer(settings, sessionManager);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down SmartAttendance companion runtime...");
                server.stop();
                sessionManager.shutdown();
            }));
            server.start();
            logger.info("SmartAttendance companion listening on {}:{} (version {})",
                    settings.host(), settings.port(), settings.version());
        } catch (Exception ex) {
            logger.error("Companion runtime failed to start: {}", ex.getMessage(), ex);
            System.exit(1);
        }
    }
}
