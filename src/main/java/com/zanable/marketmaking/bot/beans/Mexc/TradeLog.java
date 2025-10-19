package com.zanable.marketmaking.bot.beans.Mexc;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
public class TradeLog {
    /*
          "symbol":"FUSDUSDT",
      "id":"565809460614950913X1",
      "orderId":"C02__565809460614950913036",
      "orderListId":-1,
      "price":"1",
      "qty":"3",
      "quoteQty":"3",
      "commission":"0.0015",
      "commissionAsset":"USDT",
      "time":1750681724000,
      "isBuyer":true,
      "isMaker":false,
      "isBestMatch":true,
      "isSelfTrade":false,
      "clientOrderId":null
     */
    private String symbol;
    private String id;
    private String orderId;
    private int orderListId;
    private BigDecimal price;
    private BigDecimal qty;
    private BigDecimal quoteQty;
    private BigDecimal commission;
    private String commissionAsset;
    private long time;
    private boolean isBuyer;
    private boolean isMaker;
    private boolean isBestMatch;
    private boolean isSelfTrade;
    private String clientOrderId;
}
