package com.zanable.marketmaking.bot.beans.Mexc;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
public class SpotOrderResponse {
    private String symbol;
    private String orderId;
    private BigDecimal origQty;
    private BigDecimal origQuoteOrderQty;
    private BigDecimal price;
    private String type;
    private String side;
    private long transactTime;
    private long orderListId;
    private long time;
    private long updateTime;
    private String msg;
    private long code;
}
