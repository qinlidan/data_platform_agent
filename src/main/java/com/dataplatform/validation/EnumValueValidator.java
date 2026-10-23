package com.dataplatform.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 枚举值校验器
 */
public class EnumValueValidator implements ConstraintValidator<EnumValue, Object> {

    private Set<String> allowedValues;
    private boolean nullable;

    @Override
    public void initialize(EnumValue annotation) {
        Class<? extends Enum<?>> enumClass = annotation.enumClass();
        this.nullable = annotation.nullable();

        // 获取枚举的所有值（name）
        this.allowedValues = Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        // null值处理
        if (value == null) {
            return nullable;
        }

        // 字符串类型
        if (value instanceof String) {
            return allowedValues.contains(value);
        }

        // 枚举类型
        if (value instanceof Enum) {
            return allowedValues.contains(((Enum<?>) value).name());
        }

        return false;
    }
}
