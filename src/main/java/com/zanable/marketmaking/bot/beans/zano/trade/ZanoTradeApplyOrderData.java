package com.zanable.marketmaking.bot.beans.zano.trade;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ZanoTradeApplyOrderData {
    private String id;
    private String connected_order_id;
    private String hex_raw_proposal;
}
