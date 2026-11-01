package com.gitmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GitMind AI - GitHub Profile Analyzer Application.
 * 
 * This is the entry point for the Spring Boot application.
 */
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class GitMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitMindApplication.class, args);
    }
}
