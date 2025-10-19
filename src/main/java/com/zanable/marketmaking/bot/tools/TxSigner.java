package com.zanable.marketmaking.bot.tools;

import com.google.common.hash.Hashing;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.zanable.marketmaking.bot.beans.zano.*;
import com.zanable.shared.services.SigningEngine;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;

public class TxSigner {

    private LinkedHashMap<String, Object> config;

    public TxSigner(LinkedHashMap<String, Object> config) {
        this.config = config;
    }

    public ArrayList<String> signWalletTransaction(WalletTx tx) throws JOSEException {
        ArrayList<String> signedJwtTokens = new ArrayList<String>();
        ArrayList<JWTClaimsSet> claimsSetsList = convertToClaimsSet(tx);

        for (JWTClaimsSet claimsSet : claimsSetsList) {
            SignedJWT jwt = SigningEngine.signJwt((String) config.get("signKey"), claimsSet);
            signedJwtTokens.add(jwt.serialize());
        }
        return signedJwtTokens;
    }

    public SignedJWT signClaimsSet(JWTClaimsSet claimsSet) throws JOSEException {
        return SigningEngine.signJwt((String) config.get("signKey"), claimsSet);
    }

    public ArrayList<JWTClaimsSet> convertToClaimsSet(WalletTx tx) throws JOSEException {
        ArrayList<JWTClaimsSet> claimsSetsList = new ArrayList<JWTClaimsSet>();
        int vout = 0;
        for (Subtransfer txDetail : tx.getSubtransfers()) {
            String subject = "withdrawal";
            JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder();

            BigInteger realAmount = txDetail.getAmount();
            boolean isIncome = false;

            String address;

            if (txDetail.is_income()) {
                address = tx.getThisWalletAddress();
                isIncome = true;
                subject = "deposit";
                tx.setReceivingAddress(tx.getThisWalletAddress());
                tx.setReceivingAlias(tx.getThisWalletAlias());
                tx.setSendingAddress(tx.getRemoteAddress());
                tx.setSenderAlias(tx.getRemoteAlias());
                if (tx.isMining()) {
                    subject = "miningreward";
                }
            } else {
                tx.setReceivingAddress(tx.getRemoteAddress());
                tx.setReceivingAlias(tx.getRemoteAlias());
                tx.setSendingAddress(tx.getThisWalletAddress());
                tx.setSenderAlias(tx.getThisWalletAlias());
                address = tx.getRemoteAddress();
                realAmount = realAmount.negate();
            }

            String normTxId = Hashing.sha256()
                    .hashString(tx.getTxHash() + ":" + String.valueOf(vout), StandardCharsets.UTF_8)
                    .toString();

            // Setup JWT claims
            claimsSetBuilder.subject(subject)
                    .issuer((String) config.get("signKey"));

            claimsSetBuilder.claim("amount", realAmount)
                    .claim("decimals", txDetail.getAsset_info().getDecimal_point())
                    .claim("network", (String) config.get("network"))
                    .claim("asset", txDetail.getAsset_info().getTicker())
                    .claim("assetId", txDetail.getAsset_id())
                    .claim("confirmations", tx.getConfirmations())
                    .claim("txId", tx.getTxHash())
                    .claim("vout", vout)
                    .claim("height", tx.getHeight())
                    .claim("normTxId", normTxId)
                    .claim("label", tx.getPaymentId())
                    .claim("paymentId", tx.getPaymentId())
                    .claim("comment", tx.getComment())
                    .claim("sendingAddress", tx.getSendingAddress())
                    .claim("sendingAlias", tx.getSendingAlias())
                    .claim("receivingAddress", tx.getReceivingAddress())
                    .claim("receivingAlias", tx.getReceivingAlias())
                    .claim("remoteAlias", tx.getSenderAlias())
                    .claim("received", tx.getTimestamp())
                    .claim("isMining", tx.isMining())
                    .claim("isIncome", isIncome)
                    .issueTime(new Date(System.currentTimeMillis()));

            JWTClaimsSet claimsSet = claimsSetBuilder.build();

            claimsSetsList.add(claimsSet);
            vout++;
        }
        return claimsSetsList;
    }
}
