package com.zanable.marketmaking.bot.beans;

import com.zanable.marketmaking.bot.enums.TwoFactorType;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class TwoFaRegReq {
    private TwoFactorType type;
    private String code;
    private String otp;
}
