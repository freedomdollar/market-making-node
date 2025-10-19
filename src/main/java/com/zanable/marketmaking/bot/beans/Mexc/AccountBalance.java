package com.zanable.marketmaking.bot.beans.Mexc;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AccountBalance {
    // {"asset":"ZANO","free":"55","locked":"0","available":"55"}
    private String asset;
    private BigDecimal free;
    private BigDecimal locked;
    private BigDecimal available;
}
