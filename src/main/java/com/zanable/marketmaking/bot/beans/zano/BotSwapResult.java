package com.zanable.marketmaking.bot.beans.zano;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class BotSwapResult {
    private boolean accepted = false;
    private String txid;
    private String reason;

    public BotSwapResult() {

    }
}
