package com.zanable.marketmaking.bot.beans.zano.trade;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class ZanoTradeCreateOrderData {
    /*
        type: OfferType;
    side: Side;
    price: string;
    amount: string;
    pairId: number;
     */
    private OrderType type;
    private OrderSide side;
    private String price;
    private String amount;
    private long pairId;
    private transient BigDecimal zanoPrice;
}
