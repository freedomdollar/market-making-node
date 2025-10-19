package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RpcResult {
    private AssetInfo asset_descriptor;
    private TxInfo tx_info;
    private String status;
}
