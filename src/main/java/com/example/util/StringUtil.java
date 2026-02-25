package com.example.util;

public class StringUtil {

    public static final String OPEN_AI = "https://api.openai.com";
    public static final String TELEGRAM_BOT_API = "https://api.telegram.org/bot";
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

    public static final String REVIEW_STUB = "Code Review mode. Send code or a PR link.";
    public static final String INTERVIEW_STUB =
            "Interview Coach mode. Specify a topic, e.g. /interview spring.";
    public static final String HELP =
            "Use /start for commands, /review for code review, /interview <topic> for interview.";
}
