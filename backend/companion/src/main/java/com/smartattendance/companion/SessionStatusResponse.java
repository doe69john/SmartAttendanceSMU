package com.smartattendance.companion;

public record SessionStatusResponse(String status,
                                    boolean active,
                                    String sessionId,
                                    String sectionId,
                                    String modelPath,
                                    String cascadePath,
                                    String labelsPath,
                                    String startedAt,
                                    String lastHeartbeat,
                                    String scheduledStart,
                                    String scheduledEnd,
                                    int lateThresholdMinutes) {
}
