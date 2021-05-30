package com.rtsio.kubemonitor.slack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SlackNotifier {

    @Autowired
    private OkHttpClient httpClient;

    @Autowired
    private ObjectMapper objectMapper;

    public static final String SUCCESS_COLOR = "#64FF33";
    public static final String WARNING_COLOR = "#FFD133";
    public static final String ERROR_COLOR = "#FF3333";


    public void sendMessage(String text, String webhook, MessageSeverity messageSeverity) {

        SlackMessage message = new SlackMessage(List.of(
                new SlackAttachment(
                        mapMessageSeverityToColor(messageSeverity),
                        text
                )
        ));
        postMessagePayload(message, webhook);
    }

    private String mapMessageSeverityToColor(MessageSeverity messageSeverity) {
        switch (messageSeverity) {
            case WARNING:
                return WARNING_COLOR;
            case ERROR:
                return ERROR_COLOR;
            default:
                return SUCCESS_COLOR;
        }
    }

    private void postMessagePayload(SlackMessage message, String webhook) {

        String requestPayload = "";
        try {
            requestPayload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Couldn't serialize Slack payload", e);
            return;
        }

        HttpUrl url = HttpUrl.parse(webhook);

        RequestBody requestBody = RequestBody.create(MediaType.get("application/json; charset=utf-8"), requestPayload);
        Request request = new Request.Builder()
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body().string();
            if (response.code() != 200) {
                log.error("Slack returned {}, body: {}", response.code(), body);
            }
        } catch (Exception e) {
            log.error("Couldn't send to Slack", e);
        }
    }
}

