package com.gitmind.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * DTO for GitHub public event information.
 */
@Data
@Builder
public class EventDto {

    private String id;
    private String type;
    private String actorLogin;
    private String repoName;
    private String repoUrl;
    private String ref;
    private String refType;
    private Integer pushSize;
    private Boolean isPublic;
    private Instant createdAt;
}
