package com.dataplatform.knowledge.controller;

import com.dataplatform.knowledge.dto.CitationDetail;
import com.dataplatform.knowledge.service.CitationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** standaloneSetup：绕开 Spring Security 和其它并行改动，只验证控制器本身。 */
class CitationControllerTest {

    private CitationStore store;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(CitationStore.class);
        when(store.find(anyString())).thenReturn(Optional.empty());
        when(store.find("7")).thenReturn(Optional.of(List.of(
            CitationDetail.builder().index(1).chunkId(101L).text("原文一").sectionPath("第1章/第1节").build(),
            CitationDetail.builder().index(2).chunkId(102L).text("原文二").sectionPath("第2章").build())));
        mockMvc = MockMvcBuilders.standaloneSetup(new CitationController(store)).build();
    }

    @Test
    void returnsCitationDetail() throws Exception {
        mockMvc.perform(get("/api/chat/citation/2").param("conversationId", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.index").value(2))
            .andExpect(jsonPath("$.data.chunkId").value(102))
            .andExpect(jsonPath("$.data.text").value("原文二"))
            .andExpect(jsonPath("$.data.sectionPath").value("第2章"));
    }

    @Test
    void firstIndexIsOne() throws Exception {
        mockMvc.perform(get("/api/chat/citation/1").param("conversationId", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.text").value("原文一"));
    }

    @Test
    void indexZeroIs404() throws Exception {
        mockMvc.perform(get("/api/chat/citation/0").param("conversationId", "7"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void negativeIndexIs404() throws Exception {
        mockMvc.perform(get("/api/chat/citation/-1").param("conversationId", "7"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void indexBeyondCountIs404WithCount() throws Exception {
        mockMvc.perform(get("/api/chat/citation/3").param("conversationId", "7"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2")));
    }

    @Test
    void unknownConversationIs404() throws Exception {
        mockMvc.perform(get("/api/chat/citation/1").param("conversationId", "999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("999")));
    }
}
