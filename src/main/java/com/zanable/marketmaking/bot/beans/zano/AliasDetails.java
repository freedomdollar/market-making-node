package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AliasDetails {
    private String alias;
    private String baseAddress;
    private String comment;
    private String trackingKey;
}
