package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class WalletTx {
    private boolean income;
    private String txHash;
    private String paymentId;
    private String sender;
    private String senderAlias;
    private String comment;
    private String thisWalletAddress;
    private String thisWalletAlias;
    private String receivingAddress;
    private String receivingAlias;
    private String sendingAddress;
    private String sendingAlias;
    private String remoteAddress;
    private String remoteAlias;
    private long timestamp;
    private long height;
    private long confirmations;
    private long transferInternalIndex;
    private long unlockTime;
    private long txBlobSize;
    private long txType;
    private BigDecimal fee;
    private boolean isMining;
    private boolean isMixing;
    private boolean isService;
    private boolean showSender;
    private List<Subtransfer> subtransfers = new ArrayList<Subtransfer>();
    private List<ServiceEntry> serviceEntries = new ArrayList<ServiceEntry>();

    public WalletTx(JSONObject txJson) {
        this.comment = (String) txJson.get("comment");
        this.fee = new BigDecimal((long) txJson.get("fee"));
        this.fee = this.fee.movePointLeft(12);
        this.height = (long) txJson.get("height");
        this.isMining = (boolean) txJson.get("is_mining");
        this.isMixing = (boolean) txJson.get("is_mixing");
        this.isService = (boolean) txJson.get("is_service");
        this.paymentId = (String) txJson.get("payment_id");

        JSONArray senders = (JSONArray) txJson.get("remote_addresses");
        if (senders != null && !senders.isEmpty()) {
            this.sender = (String) senders.get(0);
            this.remoteAddress = this.sender;
        }
        JSONArray senderAliases = (JSONArray) txJson.get("remote_aliases");
        if (senderAliases != null && !senderAliases.isEmpty()) {
            this.senderAlias = (String) senderAliases.get(0);
        }
        JSONArray serviceEntriesJson = (JSONArray) txJson.get("service_entries");
        if (serviceEntriesJson != null && !serviceEntriesJson.isEmpty()) {
            for (Object serviceEntryObj : serviceEntriesJson.toArray()) {
                JSONObject serviceEntry = (JSONObject) serviceEntryObj;

                serviceEntries.add(new ServiceEntry((String) serviceEntry.get("body"), (long) serviceEntry.get("flags"), (String) serviceEntry.get("instruction"),
                        (String) serviceEntry.get("security"), (String) serviceEntry.get("service_id")));
            }
        }
        this.showSender = (boolean) txJson.get("show_sender");

        JSONArray subtransfersJson = (JSONArray) txJson.get("subtransfers");
        if (subtransfersJson != null && !subtransfersJson.isEmpty()) {
            for (Object subtransferObj : subtransfersJson.toArray()) {
                JSONObject subtransfer = (JSONObject) subtransferObj;
                subtransfers.add(new Subtransfer((long) subtransfer.get("amount"), (String) subtransfer.get("asset_id"),
                        (boolean) subtransfer.get("is_income")));
            }
        }

        this.timestamp = (long) txJson.get("timestamp");
        this.transferInternalIndex = (long) txJson.get("transfer_internal_index");
        this.txBlobSize = (long) txJson.get("tx_blob_size");
        this.txHash = (String) txJson.get("tx_hash");
        this.txType = (long) txJson.get("tx_type");
        this.unlockTime = (long) txJson.get("unlock_time");
    }

}
