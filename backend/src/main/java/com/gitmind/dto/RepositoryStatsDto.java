package com.gitmind.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for aggregated repository statistics.
 */
@Data
@Builder
public class RepositoryStatsDto {

    private Integer totalStars;
    private Integer totalForks;
    private Integer totalOpenIssues;
    private Long averageRepoSize;
    private String mostUsedLanguage;
    private Integer totalRepositories;
    private Integer originalRepositories;
    private Integer forkedRepositories;
}
