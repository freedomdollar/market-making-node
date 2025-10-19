package com.zanable.marketmaking.bot.beans.market;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Builder
@ToString
@Setter
public class SimplifiedTrade {
    private String firstCurrency;
    private String secondCurrency;
    private BigDecimal firstAmount;
    private BigDecimal secondAmount;
    private BigDecimal secondAmountTarget;
    private BigDecimal fee;
    private BigDecimal price;
    private String feeCurrency;
    private Timestamp timestamp;
    private UUID seqId;
    private int status;
    private String statusString;
    private String type;
}
