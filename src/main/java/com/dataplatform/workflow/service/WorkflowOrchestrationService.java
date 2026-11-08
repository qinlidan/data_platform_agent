package com.dataplatform.workflow.service;

import com.dataplatform.workflow.config.WorkflowProperties;
import com.dataplatform.workflow.dto.Domain;
import com.dataplatform.workflow.dto.IntentClassificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowOrchestrationService {

    private final IntentClassificationService intentClassificationService;
    private final WorkflowProperties workflowProperties;

    public Flux<ServerSentEvent<Map<String, Object>>> orchestrate(
        String resolvedMessage,
        String originalMessage,
        Long conversationId,
        String userId,
        String confirmationToken
    ) {
        // Classify intent
        IntentClassificationResult intent = intentClassificationService.classify(resolvedMessage);
        log.info("Intent classification result: domain={}, intentType={} for message: {}",
            intent.domain(), intent.intentType(), resolvedMessage);

        // Route based on domain
        return switch (intent.domain()) {
            case CHITCHAT -> returnFixedResponse(workflowProperties.getResponses().getChitchat());
            case OTHER -> returnFixedResponse(workflowProperties.getResponses().getOther());
            case QA, DATAPLATFORM_MANAGER -> Flux.error(
                new UnsupportedOperationException(
                    "Domain " + intent.domain() + " not implemented yet"
                )
            );
        };
    }

    private Flux<ServerSentEvent<Map<String, Object>>> returnFixedResponse(String message) {
        return Flux.just(
            ServerSentEvent.<Map<String, Object>>builder()
                .event("text")
                .data(Map.of("content", message))
                .build(),
            ServerSentEvent.<Map<String, Object>>builder()
                .event("done")
                .data(Map.of("status", "completed"))
                .build()
        );
    }
}
