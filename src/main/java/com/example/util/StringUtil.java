package com.example.util;

import java.util.regex.Pattern;

public class StringUtil {

    private StringUtil() {
    }

    public static final String TELEGRAM_BOT_API = "https://api.telegram.org/bot";
    public static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String PR_URL_REGEX =
            "https?://(?:www\\.)?github\\.com/([^/]+)/([^/]+)/pull/(\\d+)(?:/.*)?";
    public static final Pattern PR_PATTERN = Pattern.compile(PR_URL_REGEX);
    public static final String TELEGRAM_SEND_MESSAGE = "{token}/sendMessage";

    public static final String CHAT_ID = "chat_id";
    public static final String TEXT = "text";

    public static final String WELCOME = """
            DevPilot AI — developer assistant.
            Commands:
            /review — code review (paste code or PR link)
            /interview <topic> — interview mode (e.g. /interview spring)
            """;

    public static final String CMD_START = "/start";
    public static final String CMD_REVIEW = "/review";
    public static final String CMD_INTERVIEW = "/interview";

    public static final String INTERVIEW_STUB =
            "Interview Coach mode. Specify a topic, e.g. /interview spring.";
    public static final String HELP =
            "Use /start for commands, /review for code review, /interview <topic> for interview.";

    public static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    public static final int MAX_TOKENS = 2048;
    //public static final String DEFAULT_SYSTEM_PROMPT = "You are a code reviewer. Analyze the code provided. Be concise.";
}
