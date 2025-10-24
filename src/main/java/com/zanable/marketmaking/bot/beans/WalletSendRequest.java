package com.zanable.marketmaking.bot.beans;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class WalletSendRequest {
    private String address;
    private String amount;
    private String assetId;
    private String comment;
    private String paymentId;
    private String twoFa;
}
