package com.zanable.marketmaking.bot.beans;

import com.zanable.marketmaking.bot.enums.TwoFactorType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Timestamp;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class TwoFactorData {
    private long loginId;
    private String data;
    private TwoFactorType type;
    private Timestamp timestamp;
}
