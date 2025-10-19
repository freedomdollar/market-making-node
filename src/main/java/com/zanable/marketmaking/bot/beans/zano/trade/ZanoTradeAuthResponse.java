package com.zanable.marketmaking.bot.beans.zano.trade;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ZanoTradeAuthResponse {
    private boolean success;
    private String data;
}
