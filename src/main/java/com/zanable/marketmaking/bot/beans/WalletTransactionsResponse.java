package com.zanable.marketmaking.bot.beans;

import lombok.Getter;
import lombok.Setter;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class WalletTransactionsResponse {
    private int status = 500;
    private String message = "Error";
    private String type;
    private long totalItems;
    private long totalPages;
    private long currentPage;
    private JSONObject walletInfo;
    private List<WalletTransaction> data = new ArrayList<>();
}
