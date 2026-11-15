package io.github.genichimaruo.singulo.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** All-or-nothing source/target transfer, never losing energy after a rejected insertion. */
public final class EnergyTransfers {
    private EnergyTransfers() {}
    public static int move(EnergyHandler source, EnergyHandler target, int limit) {
        if (limit <= 0) return 0;
        try (var tx = Transaction.openRoot()) {
            int accepted;
            try (var probe = Transaction.open(tx)) { accepted=target.insert(source.extract(limit,probe),probe); }
            if (accepted <= 0 || source.extract(accepted,tx) != accepted || target.insert(accepted,tx) != accepted) return 0;
            tx.commit();
            return accepted;
        }
    }
    public static long moveLong(EnergyHandler source, EnergyHandler target, long limit) {
        if (limit <= 0) return 0;
        try (var tx = Transaction.openRoot()) {
            long accepted;
            try (var probe = Transaction.open(tx)) { accepted=LongEnergyHandler.insert(target,LongEnergyHandler.extract(source,limit,probe),probe); }
            if (accepted<=0 || LongEnergyHandler.extract(source,accepted,tx)!=accepted || LongEnergyHandler.insert(target,accepted,tx)!=accepted) return 0;
            tx.commit(); return accepted;
        }
    }
}