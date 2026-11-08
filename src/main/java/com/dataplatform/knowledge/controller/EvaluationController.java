package com.dataplatform.knowledge.controller;

import com.dataplatform.common.ApiResponse;
import com.dataplatform.knowledge.dto.ChunkCandidate;
import com.dataplatform.knowledge.evaluation.EvaluationCase;
import com.dataplatform.knowledge.evaluation.EvaluationCaseLoadException;
import com.dataplatform.knowledge.evaluation.EvaluationCaseRepository;
import com.dataplatform.knowledge.evaluation.EvaluationResultDTO;
import com.dataplatform.knowledge.evaluation.EvaluationRunRequest;
import com.dataplatform.knowledge.evaluation.EvaluationService;
import com.dataplatform.knowledge.service.HybridSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 检索评估接口。
 *
 * POST /run 会对每个用例调用收费的重排接口，所以限制单次用例数（knowledge.evaluation.max-cases，默认 60，
 * 超出直接报错不截断），并且同一时间只允许一次评估运行（第二个请求立即报错，不排队）。
 *
 * 安全提示：项目 SecurityConfig 目前是 anyRequest().permitAll()，这些接口没有鉴权，
 * 任何能访问服务的人都能触发收费调用。
 */
@Tag(name = "检索评估")
@RestController
@RequestMapping("/api/knowledge/evaluation")
@Slf4j
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final EvaluationCaseRepository caseRepository;
    private final HybridSearchService hybridSearchService;
    private final int maxCases;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EvaluationController(EvaluationService evaluationService,
                                EvaluationCaseRepository caseRepository,
                                HybridSearchService hybridSearchService,
                                @Value("${knowledge.evaluation.max-cases:60}") int maxCases) {
        this.evaluationService = evaluationService;
        this.caseRepository = caseRepository;
        this.hybridSearchService = hybridSearchService;
        this.maxCases = maxCases;
    }

    @Operation(summary = "获取当前评估集")
    @GetMapping("/cases")
    public ApiResponse<List<EvaluationCase>> cases() {
        try {
            return ApiResponse.success(caseRepository.loadCases());
        } catch (EvaluationCaseLoadException e) {
            log.error("评估集加载失败", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @Operation(summary = "运行四策略评估", description = "body 可选：{\"cases\":[...]}；缺省使用服务端评估集。会调用收费的重排接口")
    @PostMapping("/run")
    public ApiResponse<EvaluationResultDTO> run(@RequestBody(required = false) EvaluationRunRequest request) {
        List<EvaluationCase> cases;
        if (request != null && request.getCases() != null) {
            cases = request.getCases();
            if (cases.isEmpty()) {
                return ApiResponse.error(400, "cases 不能为空列表；不传 cases 则使用服务端评估集");
            }
            for (int i = 0; i < cases.size(); i++) {
                EvaluationCase c = cases.get(i);
                if (c == null || c.getQuery() == null || c.getQuery().isBlank()) {
                    return ApiResponse.error(400, "第 " + (i + 1) + " 条用例缺少 query");
                }
            }
        } else {
            try {
                cases = caseRepository.loadCases();
            } catch (EvaluationCaseLoadException e) {
                log.error("评估集加载失败", e);
                return ApiResponse.error(500, e.getMessage());
            }
        }
        if (cases.size() > maxCases) {
            return ApiResponse.error(400, "单次评估最多 " + maxCases + " 条用例，当前 " + cases.size()
                    + " 条；请减少用例数（可通过 knowledge.evaluation.max-cases 调整）");
        }

        if (!running.compareAndSet(false, true)) {
            return ApiResponse.error(409, "评估正在运行，请稍后再试");
        }
        try {
            return ApiResponse.success(evaluationService.evaluate(cases));
        } catch (Exception e) {
            log.error("评估运行失败", e);
            return ApiResponse.error(500, "评估运行失败: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    @Operation(summary = "按关键词列出候选 chunk（辅助人工标注 groundTruth）")
    @GetMapping("/candidates")
    public ApiResponse<List<ChunkCandidate>> candidates(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(hybridSearchService.searchCandidates(keyword, limit));
    }
}
