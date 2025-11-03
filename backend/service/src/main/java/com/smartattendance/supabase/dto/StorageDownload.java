package com.smartattendance.supabase.dto;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import reactor.core.publisher.Flux;

public class StorageDownload {

    private final Flux<DataBuffer> data;
    private final MediaType mediaType;
    private final Long contentLength;
    private final HttpHeaders headers;

    public StorageDownload(Flux<DataBuffer> data,
                           MediaType mediaType,
                           Long contentLength,
                           HttpHeaders headers) {
        this.data = data;
        this.mediaType = mediaType;
        this.contentLength = contentLength;
        this.headers = headers;
    }

    public Flux<DataBuffer> getData() {
        return data;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public Long getContentLength() {
        return contentLength;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }
}
