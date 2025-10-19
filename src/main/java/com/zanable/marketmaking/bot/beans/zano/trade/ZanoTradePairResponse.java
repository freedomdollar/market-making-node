package com.zanable.marketmaking.bot.beans.zano.trade;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ZanoTradePairResponse {
    private ZanoTradePairData data;
    private boolean success;
}
