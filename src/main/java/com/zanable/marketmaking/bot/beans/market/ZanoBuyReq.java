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
public class ZanoBuyReq {
    private long id;
    private long connectedOrderId;
    private int status;
    private Timestamp timestamp;
    private BigDecimal usdtAmount;
    private BigDecimal zanoAmount;
    private BigDecimal zanoAmountExecuted;
    private BigDecimal zanoPrice;
    private String cexOrderId;
    private UUID seqId;
}
