package com.dataplatform.workflow.dto;

public record IntentClassificationResult(Domain domain, IntentType intentType) {

    public static IntentClassificationResult fallback() {
        return new IntentClassificationResult(Domain.OTHER, IntentType.NONE);
    }
}
