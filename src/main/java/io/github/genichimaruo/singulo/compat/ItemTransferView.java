package io.github.genichimaruo.singulo.compat;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Pure routing/filter views over native storages; never mutates through legacy APIs. */
public class ItemTransferView implements ResourceHandler<ItemResource> {
    private final ResourceHandler<ItemResource> storage;
    private final IntUnaryOperator mapping;
    private final int size;
    private final BiPredicate<Integer, ItemResource> insert, extract;
    public ItemTransferView(ResourceHandler<ItemResource> storage, BiPredicate<Integer, ItemResource> insert,
                            BiPredicate<Integer, ItemResource> extract) {
        this(storage, storage.size(), i -> i, insert, extract);
    }
    public ItemTransferView(ResourceHandler<ItemResource> storage, int size, IntUnaryOperator mapping,
                            BiPredicate<Integer, ItemResource> insert, BiPredicate<Integer, ItemResource> extract) {
        this.storage = storage; this.size = size; this.mapping = mapping; this.insert = insert; this.extract = extract;
    }
    private int slot(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        return mapping.applyAsInt(index);
    }
    @Override public int size() { return size; }
    @Override public ItemResource getResource(int index) { return storage.getResource(slot(index)); }
    @Override public long getAmountAsLong(int index) { return storage.getAmountAsLong(slot(index)); }
    @Override public long getCapacityAsLong(int index, ItemResource resource) { return storage.getCapacityAsLong(slot(index), resource); }
    @Override public boolean isValid(int index, ItemResource resource) { int mapped = slot(index); return !resource.isEmpty() && insert.test(index, resource) && storage.isValid(mapped, resource); }
    @Override public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int mapped = slot(index);
        return insert.test(index, resource) ? storage.insert(mapped, resource, amount, tx) : 0;
    }
    @Override public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int mapped = slot(index);
        return extract.test(index, resource) ? storage.extract(mapped, resource, amount, tx) : 0;
    }
    public static ResourceHandler<ItemResource> combined(ResourceHandler<ItemResource>... handlers) {
        var copy = List.of(handlers);
        return new ResourceHandler<>() {
            private ResourceHandler<ItemResource> handler(int index) {
                if (index < 0) throw new IndexOutOfBoundsException(index);
                for (var h : copy) { if (index < h.size()) return h; index -= h.size(); }
                throw new IndexOutOfBoundsException(index);
            }
            private int local(int index) { for (var h : copy) { if (index < h.size()) return index; index -= h.size(); } throw new IndexOutOfBoundsException(index); }
            public int size() { return copy.stream().mapToInt(ResourceHandler::size).sum(); }
            public ItemResource getResource(int i) { return handler(i).getResource(local(i)); }
            public long getAmountAsLong(int i) { return handler(i).getAmountAsLong(local(i)); }
            public long getCapacityAsLong(int i, ItemResource r) { return handler(i).getCapacityAsLong(local(i), r); }
            public boolean isValid(int i, ItemResource r) { return handler(i).isValid(local(i), r); }
            public int insert(int i, ItemResource r, int n, TransactionContext tx) { return handler(i).insert(local(i), r, n, tx); }
            public int extract(int i, ItemResource r, int n, TransactionContext tx) { return handler(i).extract(local(i), r, n, tx); }
        };
    }
}