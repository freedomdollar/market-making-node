package com.zanable.marketmaking.bot.beans.zano;

public class WalletRpcResult<T> {
    private int id;
    private String jsonrpc;
    private T result;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public T getResult() {
        return (T) result;
    }

    public void setResult(T result) {
        this.result = result;
    }
}
