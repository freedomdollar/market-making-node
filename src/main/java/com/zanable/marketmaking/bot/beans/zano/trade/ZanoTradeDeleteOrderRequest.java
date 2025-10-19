package com.zanable.marketmaking.bot.beans.zano.trade;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class ZanoTradeDeleteOrderRequest {
    private long orderId;
    private String token;
}
