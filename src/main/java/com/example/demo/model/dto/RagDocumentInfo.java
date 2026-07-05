package com.example.demo.model.dto;

import com.example.demo.model.DocumentFileStatus;
import com.example.demo.model.SourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagDocumentInfo {
    private String sourceId;
    private String fileHash;
    private String userId;
    private String filename;
    private SourceType sourceType;
    private String minioUrl;
    private String minioPath;
    private Long fileSize;
    private DocumentFileStatus status;
    private String errorMessage;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Integer getProgressPercent() {
        return DocumentTaskView.progressPercent(status);
    }

    public String getProcessingStage() {
        return DocumentTaskView.processingStage(status);
    }

    public List<String> getPipelineSteps() {
        return DocumentTaskView.pipelineSteps(status);
    }

    public Boolean getCanRetry() {
        return status == DocumentFileStatus.FAILED && minioUrl != null && !minioUrl.isBlank();
    }

    public Boolean getCanReindex() {
        return status == DocumentFileStatus.SUCCESS && minioUrl != null && !minioUrl.isBlank();
    }
}
