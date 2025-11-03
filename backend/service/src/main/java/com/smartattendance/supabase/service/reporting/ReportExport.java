package com.smartattendance.supabase.service.reporting;

public class ReportExport {

    private final byte[] content;
    private final String contentType;
    private final String fileName;

    public ReportExport(byte[] content, String contentType, String fileName) {
        this.content = content;
        this.contentType = contentType;
        this.fileName = fileName;
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileName() {
        return fileName;
    }
}
