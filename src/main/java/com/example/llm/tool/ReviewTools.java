package com.example.llm.tool;

import com.example.llm.OpenAiRequest;

import java.util.List;
import java.util.Map;

/**
 * Tool definitions for review mode (OpenAI tool-calling).
 */
public final class ReviewTools {

    private ReviewTools() {
    }

    public static final String GET_PULL_REQUEST_DIFF = "getPullRequestDiff";

    /**
     * Tools offered to the LLM when reviewing a PR.
     */
    public static List<OpenAiRequest.Tool> reviewTools() {
        return List.of(
                OpenAiRequest.Tool.builder()
                        .type("function")
                        .function(OpenAiRequest.Tool.Function.builder()
                                .name(GET_PULL_REQUEST_DIFF)
                                .description(
                                        "Get the unified diff of a GitHub pull request by its URL. Use this to review the code changes.")
                                .parameters(Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "pr_url", Map.of(
                                                        "type", "string",
                                                        "description",
                                                        "Full URL of the GitHub pull request, e.g. https://github.com/owner/repo/pull/123"
                                                )
                                        ),
                                        "required", List.of("pr_url")
                                ))
                                .build())
                        .build()
        );
    }
}
