package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class GetBalanceRes {
    private int status;
    private long balanceLegacy;
    private long balance;
    private String balanceString;
}
