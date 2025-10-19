package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class RecentTxsAndInfo {
    private long last_item_index;
    private long total_transfers;
    private WalletTransfer[] transfers;
}
