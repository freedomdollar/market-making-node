package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigInteger;

@Getter
@Setter
@ToString
public class Subtransfer {
    private BigInteger amount;
    private String asset_id;
    private boolean is_income;
    private AssetInfo asset_info;

    public Subtransfer(long amount, String asset_id, boolean is_income) {
        this.amount = BigInteger.valueOf(amount);
        this.asset_id = asset_id;
        this.is_income = is_income;
    }
}
