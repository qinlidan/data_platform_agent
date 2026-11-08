package com.dataplatform.workflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WorkflowPropertiesTest {

    @Autowired
    private WorkflowProperties properties;

    @Test
    void testWorkflowPropertiesLoaded() {
        assertNotNull(properties);

        // Test intent classification
        assertTrue(properties.getIntentClassification().isEnabled());

        // Test RAG thresholds
        assertEquals(0.5, properties.getRag().getVectorSimThreshold());
        assertEquals(0.6, properties.getRag().getRerankThreshold());

        // Test deterministic settings
        assertTrue(properties.getDeterministic().isRequireConfirmation());

        // Test responses
        assertNotNull(properties.getResponses().getChitchat());
        assertNotNull(properties.getResponses().getOther());
        assertEquals("很高兴和你聊天！有什么数据平台相关的问题可以问我。",
                     properties.getResponses().getChitchat());
        assertEquals("抱歉，我只能回答数据平台相关的问题。",
                     properties.getResponses().getOther());
    }
}
