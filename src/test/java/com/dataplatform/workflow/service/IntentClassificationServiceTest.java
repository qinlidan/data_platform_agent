package com.dataplatform.workflow.service;

import com.dataplatform.workflow.dto.Domain;
import com.dataplatform.workflow.dto.IntentClassificationResult;
import com.dataplatform.workflow.dto.IntentType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IntentClassificationServiceTest {

    @Autowired
    private IntentClassificationService service;

    @Test
    void testIntentClassificationAccuracy() throws Exception {
        // 读取测试样例
        ObjectMapper mapper = new ObjectMapper();
        List<TestCase> cases = mapper.readValue(
            getClass().getResourceAsStream("/intent-classification-test-cases.json"),
            new TypeReference<List<TestCase>>() {}
        );

        assertEquals(20, cases.size(), "Should have 20 test cases");

        int correct = 0;
        StringBuilder errorLog = new StringBuilder();

        for (TestCase testCase : cases) {
            IntentClassificationResult result = service.classify(testCase.input());
            boolean isCorrect = result.domain() == testCase.expectedDomain()
                && result.intentType() == testCase.expectedIntentType();

            if (isCorrect) {
                correct++;
            } else {
                errorLog.append(String.format("\nFailed: \"%s\"\n  Expected: %s/%s\n  Got: %s/%s",
                    testCase.input(),
                    testCase.expectedDomain(), testCase.expectedIntentType(),
                    result.domain(), result.intentType()));
            }
        }

        double accuracy = (double) correct / cases.size();
        String message = String.format("Accuracy: %.2f%% (%d/%d correct, expected >= 80%%)",
            accuracy * 100, correct, cases.size());

        if (accuracy < 0.8) {
            message += "\nErrors:" + errorLog;
        }

        assertTrue(accuracy >= 0.8, message);
    }

    record TestCase(String input, Domain expectedDomain, IntentType expectedIntentType) {}
}
