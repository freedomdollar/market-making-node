package com.zanable.marketmaking.bot.beans.zano.trade;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ZanoTradeAuthRequest {
    private ZanoTradeAuthRequestData data;
    private boolean neverExpires;
}
