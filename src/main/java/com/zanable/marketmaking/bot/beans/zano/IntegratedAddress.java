package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IntegratedAddress {
    private String standardAddress;
    private String paymentId;

    public IntegratedAddress(String standardAddress, String paymentId) {
        this.standardAddress = standardAddress;
        this.paymentId = paymentId;
    }
}
