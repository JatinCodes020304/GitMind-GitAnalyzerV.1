package com.gitmind.controller;
import com.gitmind.dto.*;
import com.gitmind.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GitHubController {
    private final GitHubAnalysisService analysisService;
    private final ContributionService contributionService;
    @GetMapping("/{username}")
    public ResponseEntity<GitHubAnalysisDto> analyzeUser(@PathVariable String username) {
        return ResponseEntity.ok(analysisService.analyzeUser(Usernames.normalize(username)));
    }
    @GetMapping("/{username}/contributions")
    public ResponseEntity<ContributionCalendarDto> getContributions(@PathVariable String username) {
        return ResponseEntity.ok(contributionService.getContributionCalendar(Usernames.normalize(username)));
    }
}
