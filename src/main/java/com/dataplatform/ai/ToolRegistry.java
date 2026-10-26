package com.dataplatform.ai;

import com.dataplatform.ai.validation.ToolValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表 - 启动时扫描并缓存所有 @Tool 方法
 *
 * <p>优势：
 * <ul>
 *   <li>启动时反射一次，运行时零反射</li>
 *   <li>支持快速克隆（浅拷贝 ToolCallback 元数据）</li>
 *   <li>线程安全（ConcurrentHashMap）</li>
 * </ul>
 *
 * @author Data Platform Team
 */
@Component
@Slf4j
public class ToolRegistry implements ApplicationListener<ContextRefreshedEvent> {

    private final ApplicationContext applicationContext;
    private final ToolValidator toolValidator;

    // 工具实例 -> ToolCallback 原型列表的映射
    private final Map<String, List<ToolCallbackPrototype>> registry = new ConcurrentHashMap<>();

    // 是否已初始化
    private volatile boolean initialized = false;

    @Autowired
    public ToolRegistry(ApplicationContext applicationContext, ToolValidator toolValidator) {
        this.applicationContext = applicationContext;
        this.toolValidator = toolValidator;
    }

    /**
     * 监听 Spring 容器启动完成事件，扫描所有工具
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (initialized) {
            return; // 防止重复初始化（父子容器可能触发多次）
        }

        synchronized (this) {
            if (initialized) {
                return;
            }

            log.info("[Tool Registry] 开始扫描工具注册表...");
            scanTools();
            initialized = true;
            log.info("[Tool Registry] 工具注册表初始化完成，共 {} 个工具类", registry.size());
        }
    }

    /**
     * 扫描所有带 @Component 的 Tool 类
     */
    private void scanTools() {
        // 扫描所有 Spring Bean，查找包含 @Tool 方法的类
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        log.debug("[Tool Registry] 开始扫描，共 {} 个 Bean", beanNames.length);

        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);

                // 获取真实类型（处理 AOP 代理）
                Class<?> beanClass = AopUtils.getTargetClass(bean);

                // 跳过 Spring 内部类
                if (beanClass.getName().startsWith("org.springframework") ||
                    beanClass.getName().startsWith("jakarta.")) {
                    continue;
                }

                // 记录所有 Tool 类
                if (beanName.toLowerCase().contains("tool") && !beanClass.getName().startsWith("org.springframework")) {
                    log.debug("[Tool Registry] 检查 Bean: {} (class: {})", beanName, beanClass.getName());
                }

                // 检查是否有 @Tool 方法
                boolean hasTool = false;
                for (Method method : beanClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class)) {
                        hasTool = true;
                        break;
                    }
                }

                if (hasTool) {
                    registerTool(beanName, bean);
                }
            } catch (Exception e) {
                log.warn("[Tool Registry] 获取 Bean  失败: {}", beanName, e.getMessage());
            }
        }
    }

    /**
     * 注册单个工具类
     */
    private void registerTool(String beanName, Object toolInstance) {
        try {
            // 使用 Spring AI 提供的工具方法转换为 ToolCallback
            ToolCallback[] callbacks = ToolCallbacks.from(toolInstance);

            if (callbacks.length == 0) {
                log.warn("[Tool Registry] Bean {} 没有有效的 @Tool 方法", beanName);
                return;
            }

            // 保存原型
            List<ToolCallbackPrototype> prototypes = new ArrayList<>();
            for (ToolCallback callback : callbacks) {
                prototypes.add(new ToolCallbackPrototype(callback));
            }

            registry.put(beanName, prototypes);
            log.info("[Tool Registry] 已注册工具: {} ({} 个方法)", beanName, callbacks.length);

        } catch (Exception e) {
            log.error("[Tool Registry] 注册工具 {} 失败", beanName, e);
        }
    }

    /**
     * 为请求创建包装后的工具回调数组
     *
     * @param eventSink UI 事件通道
     * @param toolBeanNames 工具 Bean 名称列表（如 "jobTool", "dataSourceTool"）
     * @return 包装后的 ToolCallback 数组
     */
    public ToolCallback[] createWrappedCallbacks(
            Sinks.Many<Map<String, Object>> eventSink,
            String... toolBeanNames
    ) {
        if (!initialized) {
            throw new IllegalStateException("工具注册表尚未初始化");
        }

        List<ToolCallback> wrapped = new ArrayList<>();

        for (String beanName : toolBeanNames) {
            List<ToolCallbackPrototype> prototypes = registry.get(beanName);
            if (prototypes == null) {
                log.warn("[Tool Registry] 工具 {} 未注册", beanName);
                continue;
            }

            for (ToolCallbackPrototype prototype : prototypes) {
                // 克隆并包装
                ToolCallback original = prototype.getCallback();
                ToolCallback wrappedCallback;

                if (toolValidator != null) {
                    wrappedCallback = new ValidatedToolCallback(original, eventSink, toolValidator);
                } else {
                    wrappedCallback = new ObservableToolCallback(original, eventSink);
                }

                wrapped.add(wrappedCallback);
            }
        }

        log.debug("[Tool Registry] 为请求创建了 {} 个包装后的工具回调", wrapped.size());
        return wrapped.toArray(new ToolCallback[0]);
    }

    /**
     * 获取所有已注册的工具名称
     */
    public List<String> getRegisteredToolNames() {
        return new ArrayList<>(registry.keySet());
    }

    /**
     * 工具回调原型（保存元数据，避免重复反射）
     */
    private static class ToolCallbackPrototype {
        private final ToolCallback callback;

        public ToolCallbackPrototype(ToolCallback callback) {
            this.callback = callback;
        }

        public ToolCallback getCallback() {
            return callback;
        }
    }
}
