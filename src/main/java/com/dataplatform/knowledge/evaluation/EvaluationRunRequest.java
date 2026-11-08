package com.dataplatform.knowledge.evaluation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/** POST /run 的请求体。cases 缺省（null）时使用服务端评估集。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationRunRequest {
    private List<EvaluationCase> cases;
}
