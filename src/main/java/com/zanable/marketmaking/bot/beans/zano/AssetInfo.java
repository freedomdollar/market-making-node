package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigInteger;

@Getter
@Setter
@ToString
public class AssetInfo {
    private String asset_id;
    private BigInteger current_supply;
    private int decimal_point;
    private String full_name;
    private boolean hidden_supply;
    private String meta_info;
    private String owner;
    private String owner_eth_pub_key;
    private String ticker;
    private BigInteger total_max_supply;
}
