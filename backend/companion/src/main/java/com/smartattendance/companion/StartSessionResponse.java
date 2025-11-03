package com.smartattendance.companion;

public record StartSessionResponse(String status,
                                   String sessionId,
                                   String sectionId,
                                   String modelPath,
                                   String cascadePath,
                                   String labelsPath,
                                   long downloadedBytes) {
}
