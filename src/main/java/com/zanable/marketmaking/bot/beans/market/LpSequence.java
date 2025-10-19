package com.zanable.marketmaking.bot.beans.market;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Getter
@Setter
public class LpSequence {
    private long originalOrderId;
    private SimplifiedTrade step1;
    private SimplifiedOnChainTransaction zanoFromWallet;
    private SimplifiedTrade step2;
    private SimplifiedTrade step3;
    private SimplifiedOnChainTransaction fusdFromMexc;

    public LpSequence(long originalOrderId) {
        this.originalOrderId = originalOrderId;
    }
}
