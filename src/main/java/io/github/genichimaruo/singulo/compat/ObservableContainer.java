package io.github.genichimaruo.singulo.compat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
public class ObservableContainer extends SimpleContainer {
    private final List<Consumer<Container>> listeners = new ArrayList<>();
    public ObservableContainer(int size) { super(size); }
    public void addListener(Consumer<Container> listener) { listeners.add(listener); }
    @Override public void setChanged() {
        super.setChanged();
        listeners.forEach(listener -> listener.accept(this));
    }
}
