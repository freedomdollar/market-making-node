package com.zanable.marketmaking.bot.beans.market;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@Builder
public class FusdUsdtBuyReq {
    private long id;
    private BigDecimal usdtAmount;
    private long connectedOrderId;
    private UUID seqId;
    private Timestamp timestamp;
}
