package com.gitmind.controller;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.gitmind.service.*;
import com.gitmind.exception.GlobalExceptionHandler;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ControllerTest {
    @Test void bothControllersReturnJsonValidationErrors() throws Exception {
        var analysis = mock(GitHubAnalysisService.class);
        var ai = mock(AiAnalysisService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new GitHubController(analysis, mock(ContributionService.class)),
                new AiAnalysisController(ai, analysis)).setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String path : new String[]{"/api/github/-invalid", "/api/github/-invalid/contributions", "/api/ai/summary/-invalid"})
            mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("message").value("Invalid GitHub username format"));
        verifyNoInteractions(analysis, ai);
    }
    @Test void aiSummaryReturnsJsonObject() throws Exception {
        var analysis = mock(GitHubAnalysisService.class);
        var ai = mock(AiAnalysisService.class);
        when(ai.getDeveloperSummary(eq("user"), any())).thenReturn("{\"summary\":\"hello\"}");
        var mvc = MockMvcBuilders.standaloneSetup(new AiAnalysisController(ai, analysis)).build();
        mvc.perform(get("/api/ai/summary/User")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("summary").value("hello"));
    }
}
