package com.rtsio.kubemonitor.slack;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SlackMessage {

    private List<SlackAttachment> attachments;
}
