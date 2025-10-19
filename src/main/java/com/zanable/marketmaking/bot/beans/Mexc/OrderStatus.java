package com.zanable.marketmaking.bot.beans.Mexc;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class OrderStatus {
    /*
    {"symbol":"ZANOUSDT","orderId":"C02__593034730388221952036",
    "orderListId":-1,"clientOrderId":"11","price":"17.341",
    "origQty":"0.11","executedQty":"0.11","cummulativeQuoteQty":"1.90751",
    "status":"FILLED","stpMode":"","timeInForce":null,"type":"LIMIT",
    "side":"SELL","stopPrice":null,"icebergQty":null,"time":1757172734000,
    "updateTime":1757177392000,"isWorking":true,"origQuoteOrderQty":"1.90751"}
     */

    private String symbol;
    private String orderId;
    private int orderListId;
    private String clientOrderId;
    private BigDecimal price;
    private BigDecimal origQty;
    private BigDecimal executedQty;
    private BigDecimal cummulativeQuoteQty;
    private String status;
    private String type;
    private String side;
    private long time;
    private long updateTime;
    private boolean isWorking;
    private BigDecimal origQuoteOrderQty;
    private int code;
}
