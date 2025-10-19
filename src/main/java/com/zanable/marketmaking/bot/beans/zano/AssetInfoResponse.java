package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AssetInfoResponse {
    private AssetInfo asset_descriptor;
    private String status;
}
