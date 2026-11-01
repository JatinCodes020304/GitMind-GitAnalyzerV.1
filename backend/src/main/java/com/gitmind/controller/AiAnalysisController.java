package com.gitmind.controller;

import com.gitmind.dto.GitHubAnalysisDto;
import com.gitmind.service.AiAnalysisService;
import com.gitmind.service.GitHubAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor

public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;
    private final GitHubAnalysisService gitHubAnalysisService;
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/summary/{username}")
    public ResponseEntity<com.fasterxml.jackson.databind.JsonNode> getSummary(@PathVariable String username) throws Exception {
        username = com.gitmind.service.Usernames.normalize(username);
        log.info("Received AI summary request for user: {}", username);

        GitHubAnalysisDto githubData = gitHubAnalysisService.analyzeUser(username);
        String githubDataJson = aiAnalysisService.compactData(githubData);

        String summary = aiAnalysisService.getDeveloperSummary(username, githubDataJson);
        return ResponseEntity.ok(mapper.readTree(summary));
    }
}
