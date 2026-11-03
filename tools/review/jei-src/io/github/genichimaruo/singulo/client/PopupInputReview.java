package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.SideConfig;
import mezz.jei.api.runtime.IJeiRuntime;

/** Runs against JEI's registered screen handlers, including moved and overlapping popup bounds. */
public final class PopupInputReview {
    public static void verify(MachineScreen screen, IJeiRuntime runtime) throws Exception {
        var arrow = screen.arrowArea();
        int gx = arrow.getX() + 11, gy = arrow.getY() + 8;
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        var layout = MachineScreen.class.getDeclaredMethod("layoutPopups");
        layout.setAccessible(true);
        layout.invoke(screen);
        if (runtime.getScreenHelper().getGuiClickableArea(screen, gx, gy).findAny().isEmpty())
            throw new IllegalStateException("Uncovered recipe arrow no longer opens JEI");
        for (String name : new String[]{"selector", "sides"}) {
            var field = MachineScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            Popup popup = (Popup) field.get(screen);
            popup.open = true;
            popup.clickChrome(popup.x + 3, popup.y + 4);
            popup.drag(left, top, left + gx - 17, top + gy - 16);
            popup.release();
            layout.invoke(screen);
            if (!popup.contains(left + gx, top + gy)
                    || runtime.getScreenHelper().getGuiClickableArea(screen, gx, gy).findAny().isPresent())
                throw new IllegalStateException("JEI arrow receives input through " + name);
            if (!screen.mouseClicked(left + gx, top + gy, 0))
                throw new IllegalStateException("Popup failed to consume click");
            screen.mouseReleased(left + gx, top + gy, 0);
            popup.open = false;
            if (runtime.getScreenHelper().getGuiClickableArea(screen, gx, gy).findAny().isEmpty())
                throw new IllegalStateException("Closing popup did not restore JEI arrow input");
        }
        SideCube cube = new SideCube();
        var faces = cube.visible(100, 100, 20);
        var front = faces.stream().filter(q -> q.face() == SideConfig.Face.FRONT).findFirst().orElseThrow();
        if (faces.stream().anyMatch(q -> q.face() == SideConfig.Face.BACK)
                || cube.pick(faces, front.cx(), front.cy()) != SideConfig.Face.FRONT)
            throw new IllegalStateException("Default side cube does not show/select the front");
        cube.rotate(150, 0);
        if (cube.visible(100, 100, 20).stream().noneMatch(q -> q.face() == SideConfig.Face.BACK))
            throw new IllegalStateException("Side cube drag can no longer reveal the back");
        Singulo.LOGGER.info("SINGULO_POPUP_INPUT_COMPLETE selector sides front-facing-cube");
    }
}
