package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;

@Getter
@Setter
public class TxInfo {
    private BigInteger amount;
    private BigInteger fee;
    private String id;
    private long keeper_block;
    private String timestamp;
}
