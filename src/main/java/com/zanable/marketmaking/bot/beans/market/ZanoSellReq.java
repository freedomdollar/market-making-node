package com.zanable.marketmaking.bot.beans.market;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@Builder
@ToString
public class ZanoSellReq {
    private long id;
    private long connectedOrderId;
    private Timestamp timestamp;
    private BigDecimal amount;
    private BigDecimal amountFilled;
    private BigDecimal zanoPrice;
    private String cexOrderId;
    private UUID seqId;
}