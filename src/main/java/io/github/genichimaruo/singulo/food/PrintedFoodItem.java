package io.github.genichimaruo.singulo.food;

import io.github.genichimaruo.singulo.item.SinguloItem;

/** フードプリンターで印刷した宇宙食（値は {@link PrintedFood}）。 */
public class PrintedFoodItem extends SinguloItem {
    private final PrintedFood food;

    public PrintedFoodItem(Properties properties, int stage, PrintedFood food) {
        super(properties.food(food.food()), stage, false);
        this.food = food;
    }

    public PrintedFood printedFood() {
        return food;
    }
}
