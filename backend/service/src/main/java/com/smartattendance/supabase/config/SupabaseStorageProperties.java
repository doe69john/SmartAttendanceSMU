package com.smartattendance.supabase.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "supabase.storage")
public class SupabaseStorageProperties {

    /**
     * Default bucket that stores enrolled face images.
     */
    private String faceImageBucket = "face-images";

    /**
     * Bucket that stores trained LBPH models per section.
     */
    private String faceModelBucket = "face-models";

    /**
     * Bucket that stores archived face datasets per section.
     */
    private String faceZipBucket = "face-zips";

    /**
     * Bucket that stores installer artifacts for the native companion application.
     */
    private String companionInstallerBucket = "companion-installers";

    /**
     * Optional CDN or public base URL for storage objects. If not provided the
     * application will proxy downloads through Spring.
     */
    private String publicBaseUrl;

    /**
     * Optional Supabase credential used by background workers when no
     * request-scoped authentication context is available. May be a service role
     * key or a dedicated service user JWT.
     */
    private String serviceAccessToken;

    public String getFaceImageBucket() {
        return faceImageBucket;
    }

    public void setFaceImageBucket(String faceImageBucket) {
        if (StringUtils.hasText(faceImageBucket)) {
            this.faceImageBucket = faceImageBucket.trim();
        }
    }

    public String getFaceModelBucket() {
        return faceModelBucket;
    }

    public void setFaceModelBucket(String faceModelBucket) {
        if (StringUtils.hasText(faceModelBucket)) {
            this.faceModelBucket = faceModelBucket.trim();
        }
    }

    public String getFaceZipBucket() {
        return faceZipBucket;
    }

    public void setFaceZipBucket(String faceZipBucket) {
        if (StringUtils.hasText(faceZipBucket)) {
            this.faceZipBucket = faceZipBucket.trim();
        }
    }

    public String getCompanionInstallerBucket() {
        return companionInstallerBucket;
    }

    public void setCompanionInstallerBucket(String companionInstallerBucket) {
        if (StringUtils.hasText(companionInstallerBucket)) {
            this.companionInstallerBucket = companionInstallerBucket.trim();
        }
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = StringUtils.hasText(publicBaseUrl) ? publicBaseUrl.trim() : null;
    }

    public String getServiceAccessToken() {
        return serviceAccessToken;
    }

    public void setServiceAccessToken(String serviceAccessToken) {
        this.serviceAccessToken = StringUtils.hasText(serviceAccessToken) ? serviceAccessToken.trim() : null;
    }
}
