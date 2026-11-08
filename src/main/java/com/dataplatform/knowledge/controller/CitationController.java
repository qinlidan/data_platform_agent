package com.dataplatform.knowledge.controller;

import com.dataplatform.common.Result;
import com.dataplatform.knowledge.dto.CitationDetail;
import com.dataplatform.knowledge.service.CitationStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * 回答引用溯源：根据回答里的 [n] 查询对应参考资料的原文和章节路径。
 */
@RestController
@RequestMapping("/api/chat/citation")
@RequiredArgsConstructor
@Tag(name = "引用溯源", description = "查询回答中 [n] 对应的参考资料")
public class CitationController {

    private final CitationStore citationStore;

    @GetMapping("/{index}")
    @Operation(summary = "查询引用详情", description = "index 从 1 开始，对应回答里的 [1][2]…")
    public ResponseEntity<Result<CitationDetail>> getCitation(
            @Parameter(description = "引用序号，从 1 开始") @PathVariable int index,
            @Parameter(description = "会话 ID") @RequestParam String conversationId) {

        if (index < 1) {
            return notFound("引用序号必须从 1 开始，当前为 " + index);
        }
        Optional<List<CitationDetail>> refs = citationStore.find(conversationId);
        if (refs.isEmpty()) {
            return notFound("会话 " + conversationId + " 没有可查询的引用（可能尚未产生带引用的回答，或记录已过期）");
        }
        List<CitationDetail> list = refs.get();
        if (index > list.size()) {
            return notFound("引用序号 " + index + " 超出范围，该会话最近一次回答共有 " + list.size() + " 条引用");
        }
        return ResponseEntity.ok(Result.success(list.get(index - 1)));
    }

    private static ResponseEntity<Result<CitationDetail>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.error(404, message));
    }
}
