package com.zanable.marketmaking.bot.beans.zano.trade;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ZanoTradeCreateOrderResponse {
    private boolean success;
    private ZanoTradeOrderData data;
}
