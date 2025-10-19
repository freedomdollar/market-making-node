package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SwapProposalInfo {
    /*
    "proposal": {
      "fee_paid_by_a": 10000000000,
      "to_finalizer": [{
        "amount": 1000000000000,
        "asset_id": "97d91442f8f3c22683585eaa60b53757d49bf046a96269cef45c1bc9ff7300cc"
      }],
      "to_initiator": [{
        "amount": 10000000000,
        "asset_id": "d6329b5b1f7c0805b5c345f4957554002a2f557845f64d7645dae0e051a6498a"
      }]
    }
     */
    private long fee_paid_by_a;
    private SwapParty[] to_finalizer;
    private SwapParty[] to_initiator;

    @Getter
    @Setter
    @ToString
    public class SwapParty {
        private long amount;
        private String asset_id;
    }
}
