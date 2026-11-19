package io.github.genichimaruo.singulo.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Compare same-camera images produced by AndroidWeaponReviewHarness, using the empty scene as a colour reference. */
final class AndroidWeaponReviewChecks {
    private record Frames(BufferedImage subjects, BufferedImage background) {
        static Frames load(Path folder, String mode, String scene) throws IOException {
            String prefix = mode + "-" + scene + "-";
            var subjects = ImageIO.read(folder.resolve(prefix + "subjects.png").toFile());
            var background = ImageIO.read(folder.resolve(prefix + "empty.png").toFile());
            if (subjects.getWidth() != background.getWidth() || subjects.getHeight() != background.getHeight()) {
                throw new AssertionError("Screenshot resolution changed in " + prefix);
            }
            return new Frames(subjects, background);
        }

        // FOV is vertical. Scale the fixture regions about screen centre even if the framebuffer is HiDPI.
        int x(int reference) { return subjects.getWidth() / 2 + (reference - 600) * subjects.getHeight() / 800; }
        int y(int reference) { return reference * subjects.getHeight() / 800; }
    }

    private static int red(int rgb) { return rgb >>> 16 & 255; }
    private static int green(int rgb) { return rgb >>> 8 & 255; }
    private static int blue(int rgb) { return rgb & 255; }

    /** Brightness of the white core in a box around the free tip of the dagger. Ignore individual hot pixels. */
    private static int daggerWhite(Frames frame, BufferedImage image) {
        int[] histogram = new int[256];
        int count = 0;
        for (int y = frame.y(415); y <= frame.y(442); y++) {
            for (int x = frame.x(325); x <= frame.x(375); x++) {
                int rgb = image.getRGB(x, y);
                histogram[Math.min(red(rgb), Math.min(green(rgb), blue(rgb)))]++;
                count++;
            }
        }
        int brightest = 0;
        for (int i = 255; i >= 0; i--) {
            brightest += histogram[i];
            if (brightest >= Math.max(1, count / 50)) return i;
        }
        throw new AssertionError("Dagger tip region is empty");
    }

    /** Measure background colour retained beneath the blade's faint cyan halo, outside the white core. */
    private static void checkHalo(String scene, Frames clear, Frames glass) {
        long response = 0, backgroundEnergy = 0;
        int count = 0, area = 0;
        for (int y = clear.y(397); y <= clear.y(438); y++) {
            for (int x = clear.x(688); x <= clear.x(705); x++) {
                area++;
                int light = clear.subjects.getRGB(x, y), wall = clear.background.getRGB(x, y);
                int blueGlow = blue(light) - blue(wall);
                if (blueGlow < 20 || blueGlow > 105 || green(light) - green(wall) < 17 || red(light) - red(wall) > 75) continue;
                int backdrop = glass.background.getRGB(x, y);
                int r = red(backdrop) - red(wall), g = green(backdrop) - green(wall), b = blue(backdrop) - blue(wall);
                int magnitude = r * r + g * g + b * b;
                if (magnitude < 100) continue; // Uncoloured pixels in the patterned stair texture.
                int pixel = glass.subjects.getRGB(x, y);
                // Projection onto the observed background colour change; zero means the halo punched out the glass.
                response += r * (red(pixel) - red(light)) + g * (green(pixel) - green(light)) + b * (blue(pixel) - blue(light));
                backgroundEnergy += magnitude;
                count++;
            }
        }
        if (count < Math.max(12, area / 12) || response < 0.35 * backgroundEnergy) {
            throw new AssertionError(scene + ": blade halo or rear glass missing; pixels=" + count
                    + ", background response=" + (double) response / Math.max(1, backgroundEnergy));
        }
    }

    static void verify(Path folder, String mode) throws IOException {
        var clear = Frames.load(folder, mode, "clear");
        var rear = Frames.load(folder, mode, "red-back");
        var front = Frames.load(folder, mode, "red-front");
        int openCore = daggerWhite(clear, clear.subjects);
        int rearCore = daggerWhite(rear, rear.subjects);
        int frontCore = daggerWhite(front, front.subjects);
        if (openCore < daggerWhite(clear, clear.background) + 90 || rearCore < openCore - 20 || frontCore > rearCore - 25) {
            throw new AssertionError(mode + ": white dagger core brightness (open, rear glass, front glass) = "
                    + List.of(openCore, rearCore, frontCore));
        }
        checkHalo(mode + " red glass", clear, rear);
        checkHalo(mode + " glass stairs", clear, Frames.load(folder, mode, "stairs-back"));
        var wall = Frames.load(folder, mode, "opaque-front");
        int leaks = 0;
        for (int y = 0; y < wall.subjects.getHeight(); y++) {
            for (int x = 0; x < wall.subjects.getWidth(); x++) {
                int a = wall.subjects.getRGB(x, y), b = wall.background.getRGB(x, y);
                if (Math.abs(red(a) - red(b)) > 5 || Math.abs(green(a) - green(b)) > 5 || Math.abs(blue(a) - blue(b)) > 5) leaks++;
            }
        }
        if (leaks > 0) throw new AssertionError(mode + ": weapon visible through opaque wall at " + leaks + " pixels");
    }
}
