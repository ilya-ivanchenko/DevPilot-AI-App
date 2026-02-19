package com.example.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUpdateResponse {
    private boolean ok;
    private List<Update> result;


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Update {
        @JsonProperty("update_id")
        private Long updateId;
        private Message message;
    }


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        @JsonProperty("message_id")
        private Long messageId;
        private Chat chat;
        private String text;
    }


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chat {
        private Long id;
        private String username;
    }
}
