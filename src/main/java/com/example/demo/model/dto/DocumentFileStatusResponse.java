package com.example.demo.model.dto;

import com.example.demo.model.DocumentFile;
import com.example.demo.model.DocumentFileStatus;
import com.example.demo.model.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFileStatusResponse {

    private String sourceId;
    private String fileHash;
    private String filename;
    private SourceType sourceType;
    private Long fileSize;
    private Integer chunkCount;
    private DocumentFileStatus status;
    private String errorMessage;
    private String minioUrl;
    private LocalDateTime updatedAt;
    private Boolean canDelete;
    private Integer progressPercent;
    private String processingStage;
    private List<String> pipelineSteps;
    private Boolean canRetry;
    private Boolean canReindex;

    public static DocumentFileStatusResponse from(DocumentFile documentFile) {
        return DocumentFileStatusResponse.builder()
                .sourceId(documentFile.getSourceId())
                .fileHash(documentFile.getFileHash())
                .filename(documentFile.getFilename())
                .sourceType(documentFile.getSourceType())
                .fileSize(documentFile.getFileSize())
                .chunkCount(documentFile.getChunkCount())
                .status(documentFile.getStatus())
                .errorMessage(documentFile.getErrorMessage())
                .minioUrl(documentFile.getMinioUrl())
                .updatedAt(documentFile.getUpdatedAt())
                .canDelete(!Boolean.TRUE.equals(documentFile.getDeleted()))
                .progressPercent(DocumentTaskView.progressPercent(documentFile.getStatus()))
                .processingStage(DocumentTaskView.processingStage(documentFile.getStatus()))
                .pipelineSteps(DocumentTaskView.pipelineSteps(documentFile.getStatus()))
                .canRetry(documentFile.getStatus() == DocumentFileStatus.FAILED
                        && documentFile.getMinioUrl() != null && !documentFile.getMinioUrl().isBlank())
                .canReindex(documentFile.getStatus() == DocumentFileStatus.SUCCESS
                        && documentFile.getMinioUrl() != null && !documentFile.getMinioUrl().isBlank())
                .build();
    }
}
