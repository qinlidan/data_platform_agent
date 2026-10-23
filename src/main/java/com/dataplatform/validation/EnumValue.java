package com.dataplatform.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 枚举值校验注解
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EnumValueValidator.class)
@Documented
public @interface EnumValue {

    /**
     * 枚举类
     */
    Class<? extends Enum<?>> enumClass();

    /**
     * 错误消息
     */
    String message() default "枚举值不合法";

    /**
     * 是否允许为空
     */
    boolean nullable() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
