package com.zanable.marketmaking.bot.tools;

import com.zanable.marketmaking.bot.beans.api.ExtendedTradeChain;
import com.zanable.marketmaking.bot.beans.market.SimplifiedTrade;
import com.zanable.marketmaking.bot.enums.TradeType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

public final class PnlCalculator {

    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
    private static final int SCALE = 12;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private PnlCalculator() {}

    /** Summary DTO for reporting. */
    public record PnlSummary(
            BigDecimal sellFlowUsdt, // realized PnL from SELL flow (USDT)
            BigDecimal buyFlowUsdt,  // realized PnL from BUY  flow (USDT)
            BigDecimal totalUsdt,    // sell + buy
            int sellTradesCount,     // number of SELL rows included (filled)
            int buyTradesCount       // number of BUY  rows included (filled)
    ) {}

    /** Compute realized P&L (USDT) for a mixed list of rows over any period. */
    public static PnlSummary compute(Collection<ExtendedTradeChain> rows) {
        BigDecimal sellUsdt = ZERO;
        BigDecimal buyUsdt  = ZERO;
        int sellCount = 0;
        int buyCount  = 0;

        for (ExtendedTradeChain c : rows) {
            if (c == null || c.getType() == null) continue;

            if (c.getType() == TradeType.SELL && filled(c.getZanoSellOrder()) && filled(c.getFusdBuyOrder())) {
                // SELL flow: fUSD -> ZANO -> USDT -> fUSD
                // PnL(USDT) = fUSD_bought + USDT_leftover - fUSD_sold
                BigDecimal fusdSold = nz(c.getTokensTraded()); // 1 fUSD == 1 USDT for valuation
                BigDecimal usdtFromZano = nz(c.getZanoSellOrder().getSecondAmount());
                BigDecimal usdtSpentOnFusd = nz(c.getFusdBuyOrder().getSecondAmount());
                BigDecimal fusdBought = nz(c.getFusdBuyOrder().getFirstAmount());

                // If fees are ever present and in USDT, adjust usdtFromZano/usdtSpentOnFusd here.

                BigDecimal leftoverUsdt = usdtFromZano.subtract(usdtSpentOnFusd, MC);
                BigDecimal finalUsdtEq  = fusdBought.add(leftoverUsdt, MC);
                BigDecimal pnl = finalUsdtEq.subtract(fusdSold, MC);

                sellUsdt = sellUsdt.add(pnl, MC);
                sellCount++;

            } else if (c.getType() == TradeType.BUY && filled(c.getFusdSellOrder()) && filled(c.getZanoBuyOrder())) {
                // BUY flow: ZANO -> fUSD -> USDT -> ZANO
                // PnL(USDT) = (finalZano - initialZano)*USDT_per_ZANO + USDT_leftover
                BigDecimal initialZano = nz(c.getZanoTraded());
                BigDecimal finalZano   = nz(c.getZanoBuyOrder().getFirstAmount());
                BigDecimal deltaZano   = finalZano.subtract(initialZano, MC);

                BigDecimal usdtPerZano = effectiveUsdtPerZano(c);
                BigDecimal pnl = deltaZano.multiply(usdtPerZano, MC);

                // Any leftover USDT after the final ZANO buy (rare but safe to account for)
                BigDecimal leftoverUsdt = nz(c.getFusdSellOrder().getSecondAmount())
                        .subtract(nz(c.getZanoBuyOrder().getSecondAmount()), MC);

                pnl = pnl.add(leftoverUsdt, MC);

                buyUsdt = buyUsdt.add(pnl, MC);
                buyCount++;
            }
        }

        BigDecimal total = sellUsdt.add(buyUsdt, MC);
        return new PnlSummary(
                sellUsdt.setScale(6, RoundingMode.HALF_UP),
                buyUsdt.setScale(6, RoundingMode.HALF_UP),
                total.setScale(6, RoundingMode.HALF_UP),
                sellCount, buyCount
        );
    }

    private static boolean filled(SimplifiedTrade t) {
        return t != null && t.getStatus() == 3;
    }

    private static BigDecimal nz(BigDecimal x) {
        return x == null ? ZERO : x;
    }

    /** Choose a robust USDT/ZANO rate for conversion if zanoUsdtPrice is missing/zero. */
    private static BigDecimal effectiveUsdtPerZano(ExtendedTradeChain c) {
        if (c.getZanoUsdtPrice() != null && c.getZanoUsdtPrice().compareTo(ZERO) > 0) {
            return c.getZanoUsdtPrice();
        }
        SimplifiedTrade zbo = c.getZanoBuyOrder();
        if (filled(zbo) && zbo.getPrice() != null && zbo.getPrice().compareTo(ZERO) > 0) {
            return zbo.getPrice();
        }
        SimplifiedTrade zso = c.getZanoSellOrder();
        if (filled(zso) && zso.getPrice() != null && zso.getPrice().compareTo(ZERO) > 0) {
            return zso.getPrice();
        }
        // Last resort: derive from filled buy order amounts if available
        if (filled(zbo) && nz(zbo.getFirstAmount()).compareTo(ZERO) > 0) {
            return nz(zbo.getSecondAmount()).divide(nz(zbo.getFirstAmount()), SCALE, RoundingMode.HALF_UP);
        }
        return ZERO; // If still unknown, caller’s P&L for this row will be 0.
    }
}