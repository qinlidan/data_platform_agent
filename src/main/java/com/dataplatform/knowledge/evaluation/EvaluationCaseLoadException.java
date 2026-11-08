package com.dataplatform.knowledge.evaluation;

/** 评估集加载失败（文件缺失、JSON 非法、内容不合法）。不吞掉，原样告诉调用方。 */
public class EvaluationCaseLoadException extends RuntimeException {

    public EvaluationCaseLoadException(String message) {
        super(message);
    }

    public EvaluationCaseLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
