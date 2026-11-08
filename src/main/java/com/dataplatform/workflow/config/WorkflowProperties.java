package com.dataplatform.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "workflow")
public class WorkflowProperties {

    private IntentClassification intentClassification = new IntentClassification();
    private Rag rag = new Rag();
    private Deterministic deterministic = new Deterministic();
    private Responses responses = new Responses();

    @Data
    public static class IntentClassification {
        private boolean enabled = true;
    }

    @Data
    public static class Rag {
        private double vectorSimThreshold = 0.5;
        private double rerankThreshold = 0.6;
    }

    @Data
    public static class Deterministic {
        private boolean requireConfirmation = true;
    }

    @Data
    public static class Responses {
        private String chitchat = "很高兴和你聊天！有什么数据平台相关的问题可以问我。";
        private String other = "抱歉，我只能回答数据平台相关的问题。";
    }
}
