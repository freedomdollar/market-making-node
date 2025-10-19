package com.zanable.marketmaking.bot.beans;

import com.zanable.marketmaking.bot.beans.zano.GetWalletBalanceResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;

@Getter
@Setter
@ToString
public class ZanoWalletMeta {
    private HashMap<String, GetWalletBalanceResponse.AssetBalance> assetBalanceMap = new HashMap<>();
    private HashMap<String, Integer> assetBalanceMapHash = new HashMap<>();
    private String ident;
    private String walletAdress;
    private String walletPublicKey;
    private String rpcAddress;
    private long txIndex = 0;

    public ZanoWalletMeta(String ident, String walletAdress, String walletPublicKey, String rpcAddress, long txIndex) {
        this.ident = ident;
        this.walletAdress = walletAdress;
        this.walletPublicKey = walletPublicKey;
        this.rpcAddress = rpcAddress;
        this.txIndex = txIndex;
    }
}
