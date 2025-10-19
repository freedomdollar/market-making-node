package com.zanable.marketmaking.bot.beans.Mexc;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
@Builder
public class AccountOptions {
    private String accountType;
    private boolean canTrade;
    private boolean canWithdraw;
    private boolean canDeposit;
    private AccountBalance[] balances;

    @Getter
    @Setter
    @ToString
    @Builder
    public static class AccountBalance {
        String asset;
        BigDecimal free;
        BigDecimal locked;
    }
}
