package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZanoRpcResponse {
    private int id;
    private String jsonrpc;
    private RpcResult result;
}
