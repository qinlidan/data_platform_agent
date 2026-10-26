package com.dataplatform.controller;

import com.dataplatform.common.ApiResponse;
import com.dataplatform.entity.Conversation;
import com.dataplatform.entity.Message;
import com.dataplatform.service.ConversationService;
import com.dataplatform.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理控制器
 *
 * @author Data Platform Team
 */
@Tag(name = "AI会话管理")
@RestController
@RequestMapping("/api/conversations")
@Slf4j
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    @Operation(summary = "创建新会话")
    @PostMapping
    public ApiResponse<Map<String, Object>> createConversation(
            @RequestBody Map<String, String> request
    ) {
        String userId = request.getOrDefault("userId", "anonymous");
        String title = request.get("title");

        Long conversationId = conversationService.createConversation(userId, title);

        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversationId);
        result.put("title", title != null ? title : "新会话");

        log.info("[ConversationController] Created conversation: id={}, userId={}", conversationId, userId);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取用户的会话列表")
    @GetMapping
    public ApiResponse<List<Conversation>> listConversations(
            @RequestParam(defaultValue = "anonymous") String userId
    ) {
        List<Conversation> conversations = conversationService.listByUserId(userId);
        log.debug("[ConversationController] Listed {} conversations for user: {}", conversations.size(), userId);
        return ApiResponse.success(conversations);
    }

    @Operation(summary = "获取会话详情")
    @GetMapping("/{id}")
    public ApiResponse<Conversation> getConversation(@PathVariable Long id) {
        Conversation conversation = conversationService.getById(id);
        if (conversation == null) {
            return ApiResponse.error("会话不存在");
        }
        return ApiResponse.success(conversation);
    }

    @Operation(summary = "获取会话的消息历史")
    @GetMapping("/{id}/messages")
    public ApiResponse<List<Message>> getMessages(@PathVariable Long id) {
        List<Message> messages = messageService.listByConversationId(id);
        log.debug("[ConversationController] Retrieved {} messages for conversation: {}", messages.size(), id);
        return ApiResponse.success(messages);
    }

    @Operation(summary = "更新会话标题")
    @PatchMapping("/{id}/title")
    public ApiResponse<Void> updateTitle(
            @PathVariable Long id,
            @RequestBody Map<String, String> request
    ) {
        String title = request.get("title");
        if (title == null || title.trim().isEmpty()) {
            return ApiResponse.error("标题不能为空");
        }

        conversationService.updateTitle(id, title);
        log.info("[ConversationController] Updated title for conversation: id={}, title={}", id, title);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除会话（软删除）")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long id) {
        conversationService.softDelete(id);
        log.info("[ConversationController] Deleted conversation: id={}", id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "结束会话")
    @PostMapping("/{id}/close")
    public ApiResponse<Void> closeConversation(@PathVariable Long id) {
        conversationService.endConversation(id);
        log.info("[ConversationController] Closed conversation: id={}", id);
        return ApiResponse.success(null);
    }
}
