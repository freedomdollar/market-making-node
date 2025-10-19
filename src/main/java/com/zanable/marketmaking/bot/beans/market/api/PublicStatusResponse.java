package com.zanable.marketmaking.bot.beans.market.api;

import com.zanable.marketmaking.bot.beans.zano.trade.ZanoTradeOrderData;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class PublicStatusResponse {
    private int status;
    private String msg;
    private ZanoTradeOrderData[] activeOrders;

    private BigDecimal currentZanoPriceAverage;
    private BigDecimal currentZanoPriceAskWeighted;
    private BigDecimal currentZanoPriceBidWeighted;
}
