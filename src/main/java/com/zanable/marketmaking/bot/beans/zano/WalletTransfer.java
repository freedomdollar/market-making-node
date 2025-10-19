package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigInteger;

@Getter
@Setter
@ToString
public class WalletTransfer {
    private String comment;
    private BigInteger fee;
    private long height;
    private boolean is_mining;
    private boolean is_mixing;
    private boolean is_service;
    private String payment_id;
    private String[] remote_addresses;
    private String[] remote_aliases;
    private ServiceEntry[] service_entries;
    private boolean show_sender;
    private Subtransfer[] subtransfers;
    private long timestamp;
    private long transfer_internal_index;
    private long tx_blob_size;
    private String tx_hash;
    private long tx_type;
    private long unlock_time;
}
