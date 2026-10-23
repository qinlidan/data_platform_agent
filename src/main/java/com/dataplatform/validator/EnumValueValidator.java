package com.dataplatform.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 枚举值校验器
 */
public class EnumValueValidator implements ConstraintValidator<EnumValue, Enum<?>> {

    private Set<String> enumNames;
    private boolean nullable;

    @Override
    public void initialize(EnumValue constraintAnnotation) {
        Class<? extends Enum<?>> enumClass = constraintAnnotation.enumClass();
        this.nullable = constraintAnnotation.nullable();

        // 获取枚举类的所有枚举值名称
        this.enumNames = Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isValid(Enum<?> value, ConstraintValidatorContext context) {
        // 如果允许为空且值为空，则校验通过
        if (nullable && value == null) {
            return true;
        }

        // 如果不允许为空且值为空，则校验失败
        if (value == null) {
            return false;
        }

        // 校验枚举值是否在允许的范围内
        return enumNames.contains(value.name());
    }
}
