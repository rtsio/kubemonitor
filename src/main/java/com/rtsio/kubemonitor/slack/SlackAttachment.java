package com.rtsio.kubemonitor.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SlackAttachment {

    private String color;
    private String text;
    @JsonProperty("mrkdwn_in")
    private List<String> mrkdwnIn;

    public SlackAttachment(String color, String text) {

        this.color = color;
        this.text = text;
        this.mrkdwnIn = List.of("text");
    }
}
