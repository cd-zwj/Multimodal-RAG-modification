package com.example.demo.model.dto;

import com.example.demo.model.DocumentFileStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTaskViewTest {

    @Test
    void ragDocumentInfoShouldExposeTaskProgressAndActions() {
        RagDocumentInfo failed = new RagDocumentInfo();
        failed.setStatus(DocumentFileStatus.FAILED);
        failed.setMinioUrl("http://minio/doc.pdf");

        assertThat(failed.getProgressPercent()).isEqualTo(100);
        assertThat(failed.getProcessingStage()).isEqualTo("失败");
        assertThat(failed.getPipelineSteps()).contains("失败:current");
        assertThat(failed.getCanRetry()).isTrue();
        assertThat(failed.getCanReindex()).isFalse();

        RagDocumentInfo success = new RagDocumentInfo();
        success.setStatus(DocumentFileStatus.SUCCESS);
        success.setMinioUrl("http://minio/doc.pdf");

        assertThat(success.getCanRetry()).isFalse();
        assertThat(success.getCanReindex()).isTrue();
        assertThat(success.getPipelineSteps()).contains("完成:current");
    }
}
