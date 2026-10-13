package com.gitmind.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Represents a GitHub public event.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubEvent {

    private String id;
    private String type;
    private Actor actor;
    private Repo repo;
    private Payload payload;
    private Boolean isPublic;
    private Instant createdAt;

    @JsonProperty("public")
    public void setPublicFromJson(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    @JsonProperty("created_at")
    public void setCreatedAtFromJson(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Actor {
        private Long id;
        private String login;



    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repo {
        private Long id;
        private String name;
        private String url;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private String ref;
        private String refType;
        private Integer size;

        @JsonProperty("ref_type")
        public void setRefTypeFromJson(String refType) {
            this.refType = refType;
        }




    }
}
