package com.zanable.marketmaking.bot.beans;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TelegramChannel {
    private long channelId;
    private String channelName;
}
