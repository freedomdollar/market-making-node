package com.zanable.marketmaking.bot.beans.zano.trade;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class ZanoTradeUserOrdersPageRequest {
    private String token;
    private long pairId;
}
