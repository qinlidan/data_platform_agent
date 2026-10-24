package com.dataplatform.ai.confirmation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfirmationInterceptor {

    private final ConfirmationService confirmationService;

    @Around("@annotation(com.dataplatform.ai.confirmation.RequireConfirmation)")
    public Object intercept(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireConfirmation annotation = method.getAnnotation(RequireConfirmation.class);

        String toolName = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();

        // 如果已有确认token，验证后放行
        if (ConfirmationContext.hasToken()) {
            String token = ConfirmationContext.getToken();
            if (confirmationService.validateToken(token, toolName, methodName)) {
                log.info("[Confirmation] Token 验证通过: {}.{}", toolName, methodName);
                return joinPoint.proceed();
            } else {
                throw new SecurityException("无效的确认token");
            }
        }

        // 首次调用，生成token并抛异常
        Map<String, Object> params = buildParameterMap(method, joinPoint.getArgs());
        String token = confirmationService.generateToken(toolName, methodName, params);

        log.info("[Confirmation] 需要确认: {} (风险级别: {})", annotation.operation(), annotation.riskLevel());
        throw new NeedConfirmationException(annotation.operation(), annotation.riskLevel(), token);
    }

    private Map<String, Object> buildParameterMap(Method method, Object[] args) {
        Map<String, Object> params = new HashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            params.put(parameters[i].getName(), args[i]);
        }
        return params;
    }
}
