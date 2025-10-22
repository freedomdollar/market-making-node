package com.zanable.marketmaking.bot.beans;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ZanoAddressQueryResponse {
    private String address;
    private String baseAddress;
    private String paymentId;
    private String alias;
    private String aliasComment;
}
