package com.zanable.marketmaking.bot.beans.Mexc;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SpotOptions {
    private String timeInForce;
    private String quantity;
    private String quoteOrderQty;
    private String price;
    private String newClientOrderId;
}
