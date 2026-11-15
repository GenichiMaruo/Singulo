import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.energy.EnergyTransfers;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class EnergyTransactionTest {
    static int checks;
    static void check(boolean state,String message){checks++;if(!state)throw new AssertionError(message);}
    public static void main(String[] args){
        int[] notifications={0};
        var e=new SinguloEnergyStorage(100,100,100,()->notifications[0]++);
        try(var tx=Transaction.openRoot()){check(e.insert(70,tx)==70,"insert");check(e.getEnergyStored()==70,"visible tentative state");check(notifications[0]==0,"no callback before commit");}
        check(e.getEnergyStored()==0&&notifications[0]==0,"root rollback");
        try(var tx=Transaction.openRoot()){
            e.insert(60,tx);
            try(var nested=Transaction.open(tx)){e.extract(40,nested);}
            check(e.getEnergyStored()==60,"nested rollback");
            try(var nested=Transaction.open(tx)){e.insert(20,nested);nested.commit();}
            check(e.getEnergyStored()==80,"nested commit");tx.commit();
        }
        check(e.getEnergyStored()==80&&notifications[0]==1,"single root commit callback");
        try(var tx=Transaction.openRoot()){
            e.extract(20,tx);try(var nested=Transaction.open(tx)){e.insert(40,nested);nested.commit();}
        }
        check(e.getEnergyStored()==80&&notifications[0]==1,"nested commit undone by outer abort");
        check(e.receiveEnergy(20,true)==20&&e.getEnergyStored()==80,"legacy simulation");
        e.resizeCapacity(50);check(e.getEnergyStored()==80&&e.receiveEnergy(20,false)==0,"downgrade preserves surplus");
        check(e.consume(31)&&e.getEnergyStored()==49,"surplus consumable");
        int before=notifications[0];e.generate(Integer.MAX_VALUE);check(e.getEnergyStored()==50&&notifications[0]==before+1,"generation saturates");
        check(!e.consume(-1),"negative consume rejected");
        boolean negative=false;try(var tx=Transaction.openRoot()){e.insert(-1,tx);}catch(IllegalArgumentException ex){negative=true;}check(negative,"negative native rejected");
        var a=new SinguloEnergyStorage(100,100,100,()->{});var b=new SinguloEnergyStorage(100,100,100,()->{});a.setEnergy(70);b.setEnergy(80);
        check(EnergyTransfers.move(a,b,50)==20&&a.getEnergyStored()==50&&b.getEnergyStored()==100,"atomic transfer bounded");
        check(EnergyTransfers.move(a,b,50)==0&&a.getEnergyStored()==50,"full target no debit");
        var largeSource=new LargeEnergy(9_000_000_000L,10_000_000_000L);
        var largeTarget=new LargeEnergy(0,10_000_000_000L);
        check(EnergyTransfers.moveLong(largeSource,largeTarget,5_000_000_000L)==5_000_000_000L,"long transfer exceeds int");
        check(largeSource.amount==4_000_000_000L&&largeTarget.amount==5_000_000_000L,"long conservation");
        try(var tx=Transaction.openRoot()){largeTarget.extractLong(3_000_000_000L,tx);}
        check(largeTarget.amount==5_000_000_000L,"long rollback");
        check(largeTarget.receiveLong(7_000_000_000L,true)==5_000_000_000L&&largeTarget.amount==5_000_000_000L,"long legacy simulation");
        var inconsistent=new SinguloEnergyStorage(100,100,100,()->{}) {
            int calls;
            @Override public int insert(int n,net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                int inserted=super.insert(n,tx); return ++calls==1?inserted:0;
            }
        };
        a.setEnergy(70);
        check(EnergyTransfers.move(a,inconsistent,50)==0&&a.getEnergyStored()==70&&inconsistent.getEnergyStored()==0,"mismatched actual insertion rolls both back");
        System.out.println("Energy transactions: "+checks+" checks passed");
    }
    static final class LargeEnergy extends net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Long>
            implements io.github.genichimaruo.singulo.energy.LongEnergyHandler {
        long amount,capacity;
        LargeEnergy(long amount,long capacity){this.amount=amount;this.capacity=capacity;}
        protected Long createSnapshot(){return amount;}
        protected void revertToSnapshot(Long before){amount=before;}
        public long insertLong(long n,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
            if(n<0)throw new IllegalArgumentException();long take=Math.min(n,capacity-amount);updateSnapshots(tx);amount+=take;return take;
        }
        public long extractLong(long n,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
            if(n<0)throw new IllegalArgumentException();long take=Math.min(n,amount);updateSnapshots(tx);amount-=take;return take;
        }
        public long getAmountAsLong(){return amount;}
        public long getCapacityAsLong(){return capacity;}
        public boolean canReceive(){return true;}
        public boolean canExtract(){return true;}
    }
}
