package io.github.genichimaruo.singulo.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** One-way native capability to synchronous machine-tick consumer view. Never expose as a native provider. */
@SuppressWarnings("removal")
public final class EnergyResourceView implements LongEnergyStorage {
    private final EnergyHandler handler;
    private EnergyResourceView(EnergyHandler handler){this.handler=handler;}
    public static @Nullable EnergyResourceView of(@Nullable EnergyHandler handler){return handler==null?null:new EnergyResourceView(handler);}
    @Override public long receiveLong(long amount,boolean simulate){
        if(amount<=0)return 0;
        try(var tx=Transaction.openRoot()){long n=LongEnergyHandler.insert(handler,amount,tx);if(!simulate)tx.commit();return n;}
    }
    @Override public long extractLong(long amount,boolean simulate){
        if(amount<=0)return 0;
        try(var tx=Transaction.openRoot()){long n=LongEnergyHandler.extract(handler,amount,tx);if(!simulate)tx.commit();return n;}
    }
    @Override public int receiveEnergy(int amount,boolean simulate){return (int)receiveLong(amount,simulate);}
    @Override public int extractEnergy(int amount,boolean simulate){return (int)extractLong(amount,simulate);}
    @Override public int getEnergyStored(){return handler.getAmountAsInt();}
    @Override public int getMaxEnergyStored(){return handler.getCapacityAsInt();}
    @Override public boolean canReceive(){return receiveEnergy(1,true)>0;}
    @Override public boolean canExtract(){return extractEnergy(1,true)>0;}
}
