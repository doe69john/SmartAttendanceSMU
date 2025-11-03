package com.smartattendance.util;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Thin wrapper around {@link java.util.logging.Logger} writing to
 * <code>attendance.log</code>.
 */
public final class LoggerFacade {
    private static final Logger LOGGER = Logger.getLogger("SmartAttendance");

    static {
        try {
            FileHandler handler = new FileHandler("attendance.log", true);
            handler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(handler);
            LOGGER.setUseParentHandlers(false);
        } catch (IOException e) {
            // If logging setup fails, print stack trace so failures are visible.
            e.printStackTrace();
        }
    }

    private LoggerFacade() {}

    public static void info(String msg) {
        LOGGER.info(msg);
    }

    public static void warn(String msg) {
        LOGGER.warning(msg);
    }

    public static void error(String msg, Throwable t) {
        LOGGER.log(Level.SEVERE, msg, t);
    }
}

