package com.zanable.marketmaking.bot.beans;

import com.zanable.marketmaking.bot.tools.PnlCalculator;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PnlSummaryBean {
    private PnlCalculator.PnlSummary sellPnlSummary;
    private PnlCalculator.PnlSummary buyPnlSummary;
}
