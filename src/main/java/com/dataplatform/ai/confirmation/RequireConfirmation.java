package com.dataplatform.ai.confirmation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireConfirmation {
    String operation();  // 操作描述，如"删除作业"
    String riskLevel() default "medium";  // 风险等级: low/medium/high
}
