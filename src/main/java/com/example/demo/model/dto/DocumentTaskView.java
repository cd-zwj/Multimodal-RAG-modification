package com.example.demo.model.dto;

import com.example.demo.model.DocumentFileStatus;

import java.util.ArrayList;
import java.util.List;

final class DocumentTaskView {

    private DocumentTaskView() {
    }

    static int progressPercent(DocumentFileStatus status) {
        if (status == null) return 0;
        return switch (status) {
            case UPLOADING -> 10;
            case UPLOAD_SUCCESS -> 25;
            case PROCESSING -> 35;
            case CHUNKING -> 55;
            case VECTORIZING -> 80;
            case REINDEXING -> 45;
            case SUCCESS -> 100;
            case FAILED -> 100;
        };
    }

    static String processingStage(DocumentFileStatus status) {
        if (status == null) return "未知";
        return switch (status) {
            case UPLOADING -> "上传";
            case UPLOAD_SUCCESS -> "排队";
            case PROCESSING -> "解析";
            case CHUNKING -> "分块";
            case VECTORIZING -> "向量化";
            case REINDEXING -> "重索引";
            case SUCCESS -> "完成";
            case FAILED -> "失败";
        };
    }

    static List<String> pipelineSteps(DocumentFileStatus status) {
        List<String> steps = new ArrayList<>();
        steps.add(step("上传", isDone(status, DocumentFileStatus.UPLOADING), isCurrent(status, DocumentFileStatus.UPLOADING)));
        steps.add(step("排队", isDone(status, DocumentFileStatus.UPLOAD_SUCCESS), isCurrent(status, DocumentFileStatus.UPLOAD_SUCCESS)));
        steps.add(step("解析", isDone(status, DocumentFileStatus.PROCESSING), isCurrent(status, DocumentFileStatus.PROCESSING)));
        steps.add(step("分块", isDone(status, DocumentFileStatus.CHUNKING), isCurrent(status, DocumentFileStatus.CHUNKING)));
        steps.add(step("向量化", isDone(status, DocumentFileStatus.VECTORIZING), isCurrent(status, DocumentFileStatus.VECTORIZING)));
        if (status == DocumentFileStatus.FAILED) {
            steps.add("失败:current");
        } else {
            steps.add(step("完成", status == DocumentFileStatus.SUCCESS, status == DocumentFileStatus.SUCCESS));
        }
        return steps;
    }

    private static String step(String label, boolean done, boolean current) {
        if (current) return label + ":current";
        if (done) return label + ":done";
        return label + ":pending";
    }

    private static boolean isCurrent(DocumentFileStatus status, DocumentFileStatus target) {
        return status == target || (status == DocumentFileStatus.REINDEXING && target == DocumentFileStatus.PROCESSING);
    }

    private static boolean isDone(DocumentFileStatus status, DocumentFileStatus target) {
        if (status == null || status == DocumentFileStatus.FAILED) return false;
        if (status == DocumentFileStatus.SUCCESS) return true;
        int current = progressPercent(status);
        int targetProgress = progressPercent(target);
        return current > targetProgress;
    }
}
