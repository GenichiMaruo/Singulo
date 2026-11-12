package singulo.packaging;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

final class LosslessPng {
    static byte[] compact(byte[] original) throws IOException {
        // Only re-encode plain 8-bit RGB(A). Preserve profiles, animation, text and future chunks.
        if (original.length < 33 || original[24] != 8 || (original[25] != 2 && original[25] != 6)) {
            return original;
        }
        ByteBuffer png = ByteBuffer.wrap(original);
        png.position(8);
        while (png.remaining() >= 12) {
            int length = png.getInt();
            byte[] chunkType = new byte[4];
            png.get(chunkType);
            String type = new String(chunkType, StandardCharsets.US_ASCII);
            if (length < 0 || length > png.remaining() - 4) {
                throw new IOException("Invalid PNG chunk length");
            }
            if (!type.equals("IHDR") && !type.equals("IDAT") && !type.equals("IEND")) {
                return original;
            }
            png.position(png.position() + length + 4);
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        byte[] best = smaller(original, encode(image));
        BufferedImage indexed = indexedImage(width, height, pixels);
        if (indexed != null) {
            best = smaller(best, encode(indexed));
        }
        if (best != original) {
            BufferedImage check = ImageIO.read(new ByteArrayInputStream(best));
            if (!Arrays.equals(pixels, check.getRGB(0, 0, width, height, null, 0, width))) {
                return original;
            }
        }
        return best;
    }

    private static BufferedImage indexedImage(int width, int height, int[] pixels) {
        Map<Integer, Integer> indices = new LinkedHashMap<>();
        for (int pixel : pixels) {
            indices.putIfAbsent(pixel, indices.size());
            if (indices.size() > 256) {
                return null;
            }
        }
        int[] colors = indices.keySet().stream().mapToInt(Integer::intValue).toArray();
        int bits = colors.length <= 2 ? 1 : colors.length <= 4 ? 2 : colors.length <= 16 ? 4 : 8;
        IndexColorModel palette = new IndexColorModel(bits, colors.length, colors, 0, true, -1,
                java.awt.image.DataBuffer.TYPE_BYTE);
        WritableRaster raster = palette.createCompatibleWritableRaster(width, height);
        for (int i = 0; i < pixels.length; i++) {
            raster.setSample(i % width, i / width, 0, indices.get(pixels[i]));
        }
        return new BufferedImage(palette, raster, false, null);
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(stream);
            ImageWriteParam options = writer.getDefaultWriteParam();
            options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            options.setCompressionQuality(0.0f); // JDK PNG writer's highest lossless compression.
            writer.write(null, new IIOImage(image, null, null), options);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static byte[] smaller(byte[] current, byte[] candidate) {
        return jarSize(candidate) < jarSize(current) ? candidate : current;
    }

    private static int jarSize(byte[] data) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(data);
            deflater.finish();
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                deflater.deflate(buffer);
            }
            return deflater.getTotalOut();
        } finally {
            deflater.end();
        }
    }
}
