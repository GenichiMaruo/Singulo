package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.data.PackBalance;
import io.github.genichimaruo.singulo.generated.ServerConfig;

/** A quote for additional work. Credit is the unused fraction of the last whole FE, in units of 1/denominator. */
record PumpPowerCost(int activationFe, int includedMb, long numerator, long denominator, long credit) {
    static final int MILLI_FE = 1000;
    // Distance in mB between the two configured operating points, 1 and 2,100,000,000.
    static final int FLOW_SPAN = 2_100_000_000 - 1;

    static PumpPowerCost current(int tickCredit, int prepaidMilliFe) {
        double fixedPrice = PackBalance.value(ServerConfig.PUMP_FE_PER_MB);
        if (fixedPrice > 0) {
            long rate = Math.max(1, Math.round(fixedPrice * MILLI_FE));
            return new PumpPowerCost(0, 0, rate, MILLI_FE, prepaidMilliFe);
        }
        int start = PackBalance.value(ServerConfig.PUMP_START_FE);
        long rise = Math.max(0, PackBalance.value(ServerConfig.PUMP_FE_AT_HIGH_FLOW) - start);
        if (tickCredit >= 0) return new PumpPowerCost(0, 0, rise, FLOW_SPAN, tickCredit);
        return new PumpPowerCost(start, 1, rise, FLOW_SPAN, (long) prepaidMilliFe * FLOW_SPAN / MILLI_FE);
    }

    boolean fixedPrice() { return denominator == MILLI_FE; }

    long affordable(long offeredFe) {
        if (offeredFe < activationFe) return 0;
        if (numerator == 0) return Long.MAX_VALUE;
        long budget = Math.min(Integer.MAX_VALUE, offeredFe) - activationFe;
        return includedMb + (budget * denominator + credit) / numerator;
    }

    // Only called for amounts bounded by affordable(), so even the largest configured rates fit in a long.
    int charge(long amount) {
        if (amount == 0) return 0;
        long due = (amount - includedMb) * numerator - credit;
        return activationFe + (int) Math.max(0L, Math.ceilDiv(due, denominator));
    }

    int remainingCredit(long amount, int charged) {
        return (int) (credit + (long) (charged - activationFe) * denominator - (amount - includedMb) * numerator);
    }
}
