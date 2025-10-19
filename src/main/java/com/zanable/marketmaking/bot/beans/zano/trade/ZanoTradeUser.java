package com.zanable.marketmaking.bot.beans.zano.trade;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ZanoTradeUser {
    private String address;
    private String alias;
    private String createdAt;
    private boolean isAdmin;
    private String updatedAt;
}
