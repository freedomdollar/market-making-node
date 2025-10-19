package com.zanable.marketmaking.bot.beans.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class ZanoDaemonStatus {
    private int status;
    private long height;
}
