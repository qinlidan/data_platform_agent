package com.dataplatform.ai.confirmation;

import lombok.Getter;

@Getter
public class NeedConfirmationException extends RuntimeException {

    private final String operation;
    private final String riskLevel;
    private final String confirmationToken;

    public NeedConfirmationException(String operation, String riskLevel, String confirmationToken) {
        super("需要用户确认：" + operation);
        this.operation = operation;
        this.riskLevel = riskLevel;
        this.confirmationToken = confirmationToken;
    }
}
