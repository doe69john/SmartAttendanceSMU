package com.smartattendance.supabase.service.companion;

import org.springframework.util.StringUtils;

/**
 * Utility for scoping companion installer paths so that each Git branch can
 * publish/download installers from a dedicated folder. The current branch
 * uses the {@code main} namespace.
 */
final class CompanionReleasePaths {

    private static final String BRANCH_FOLDER = "main";

    private CompanionReleasePaths() {
    }

    static String scoped(String relativePath) {
        String normalized = relativePath != null ? relativePath.trim() : "";
        if (StringUtils.hasText(normalized)) {
            normalized = normalized.replaceAll("^/+", "");
            if (normalized.isEmpty()) {
                return BRANCH_FOLDER;
            }
            return BRANCH_FOLDER + "/" + normalized;
        }
        return BRANCH_FOLDER;
    }
}
