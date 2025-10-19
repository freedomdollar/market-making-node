package com.zanable.marketmaking.bot.beans.Mexc;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.BigInteger;

@Getter
@Setter
@ToString
public class WithdrawalBean {
    /*
    {
    {
        "id": "bb17a2d452684f00a523c015d512a341",
        "txId": null,
        "coin": "EOS",
        "network": "EOS",
        "address": "zzqqqqqqqqqq",
        "amount": "10",
        "transferType": 0,
        "status": 3,
        "transactionFee": "0",
        "confirmNo": null,
        "applyTime": 1665300874000,
        "remark": "",
        "memo": "MX10086",
        "transHash": "0x0ced593b8b5adc9f600334d0d7335456a7ed772ea5547beda7ffc4f33a065c",
        "updateTime": 1712134082000,
        "coinId": "128f589271cb495b03e71e6323eb7be",
        "vcoinId": "af42c6414b9a46c8869ce30fd51660f"
  }
  }
     */
    private String id;
    private String txId;
    private String coin;
    private String address;
    private BigDecimal amount;
    private int transferType;
    private int status;
    private String network;
    private BigDecimal transactionFee;
    private BigInteger confirmNo;
    private long applyTime;
    private String remark;
    private String memo;
    private String transHash;
    private long updateTime;
    private String coinId;
    private String vcoinId;
}