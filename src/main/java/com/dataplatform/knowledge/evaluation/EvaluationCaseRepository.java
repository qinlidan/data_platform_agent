package com.dataplatform.knowledge.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 评估集存储：默认读 classpath:evaluation/evaluation-cases.json，可用
 * knowledge.evaluation.cases-location（如 file:/data/cases.json）覆盖。
 * 每次调用都重新读取，文件改了不用重启（classpath 资源仍需重新打包）。
 * 加载失败一律抛 {@link EvaluationCaseLoadException}。
 */
@Component
public class EvaluationCaseRepository {

    private final ObjectMapper objectMapper;
    private final Resource resource;

    public EvaluationCaseRepository(
            ObjectMapper objectMapper,
            @Value("${knowledge.evaluation.cases-location:classpath:evaluation/evaluation-cases.json}") Resource resource) {
        this.objectMapper = objectMapper;
        this.resource = resource;
    }

    public List<EvaluationCase> loadCases() {
        String where = resource.getDescription();
        if (!resource.exists()) {
            throw new EvaluationCaseLoadException("评估集文件不存在: " + where);
        }
        List<EvaluationCase> cases;
        try (InputStream in = resource.getInputStream()) {
            cases = objectMapper.readValue(in, new TypeReference<List<EvaluationCase>>() {
            });
        } catch (IOException e) {
            throw new EvaluationCaseLoadException("评估集解析失败（需要 JSON 数组）: " + where + " - " + e.getMessage(), e);
        }
        if (cases == null) {
            throw new EvaluationCaseLoadException("评估集内容为空: " + where);
        }
        for (int i = 0; i < cases.size(); i++) {
            EvaluationCase c = cases.get(i);
            if (c == null || c.getQuery() == null || c.getQuery().isBlank()) {
                throw new EvaluationCaseLoadException("评估集第 " + (i + 1) + " 条缺少 query: " + where);
            }
            c.setCategory(c.resolvedCategory());
        }
        return cases;
    }
}
