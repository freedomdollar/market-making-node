package com.zanable.marketmaking.bot.beans.zano.trade;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ZanoTradePairData {
    private long id;
    private ZanoTradeCurrency first_currency;
    private ZanoTradeCurrency second_currency;
}
