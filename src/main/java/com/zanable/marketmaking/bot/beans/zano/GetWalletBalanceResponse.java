package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigInteger;

@Getter
@Setter
@ToString
public class GetWalletBalanceResponse {
    private BigInteger balance;
    private AssetBalance[] balances;
    private BigInteger unlocked_balance;

    @Getter
    @Setter
    @ToString
    public class AssetBalance {
        private AssetInfo asset_info;
        private BigInteger awaiting_in;
        private BigInteger awaiting_out;
        private BigInteger outs_amount_max;
        private BigInteger outs_amount_min;
        private long outs_count;
        private BigInteger total;
        private BigInteger unlocked;
    }
}
