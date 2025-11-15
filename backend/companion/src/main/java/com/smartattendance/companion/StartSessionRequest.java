package com.smartattendance.companion;

import java.util.List;
import java.util.Map;

public record StartSessionRequest(String sessionId,
                                  String sectionId,
                                  String modelUrl,
                                  String cascadeUrl,
                                  String labelsUrl,
                                  String backendBaseUrl,
                                  List<String> missingStudentIds,
                                  Map<String, String> labels,
                                  String authToken,
                                  String scheduledStart,
                                  String scheduledEnd,
                                  Integer lateThresholdMinutes) {
}
