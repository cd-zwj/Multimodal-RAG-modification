package com.example.demo.model;

public enum DocumentFileStatus {
    UPLOADING,
    UPLOAD_SUCCESS,
    PROCESSING,
    REINDEXING,
    CHUNKING,
    VECTORIZING,
    SUCCESS,
    FAILED;

    public boolean isProcessing() {
        return this == UPLOADING
                || this == UPLOAD_SUCCESS
                || this == PROCESSING
                || this == REINDEXING
                || this == CHUNKING
                || this == VECTORIZING;
    }

    public boolean canInstantUpload() {
        return this == SUCCESS;
    }
}
