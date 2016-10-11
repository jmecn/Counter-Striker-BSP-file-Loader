package trb.jme.imaging;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.imageio.*;


public class ImageUtility {
    static HashMap intArrays = new HashMap();
    static HashMap images = new HashMap();

    /**
     * finds the maximum image bounds for the non-alpha masked object.  This
     * assumes that the image has portions of it with an alpha of zero.
     */
    public static Rectangle alphaBounds(BufferedImage image) {
        Rectangle r = new Rectangle();

        int width = image.getWidth();
        int height = image.getHeight();

        // start with max bounds
        int lx = 0;
        int ly = 0;
        int ux = width - 1;
        int uy = height - 1;

        // mark the bounds as "not found"
        boolean left = false;
        boolean top = false;
        boolean bottom = false;
        boolean right = false;

        // pull the data out
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        // now scan for the X bounds
        for (int x = 0; x < width; x++) {
            // scan from top to bottom
            for (int y = 0; y < height; y++) {
                int alphaleft = (pixels[(y * width) + x] >> 24);
                int alpharight = (pixels[(y * width) + (width - x - 1)] >> 24);

                if ((!left) && (alphaleft != 0)) {
                    lx = x;
                    left = true;
                }

                if ((!right) && (alpharight != 0)) {
                    ux = width - x - 1;
                    right = true;
                }
            }
        }

        // now scan for the Y bounds
        for (int y = 0; y < height; y++) {
            // scan from left to right
            for (int x = 0; x < width; x++) {
                int alphatop = (pixels[(y * width) + x] >> 24);
                int alphabottom = (pixels[((height - y - 1) * width) + x] >> 24);

                if ((!top) && (alphatop != 0)) {
                    ly = y;
                    top = true;
                }

                if ((!bottom) && (alphabottom != 0)) {
                    uy = height - y - 1;
                    bottom = true;
                }
            }
        }

        System.out.println("Image alpha bounds : " + lx + "," + ly + " -> " +
            ux + "," + uy);
        r.setBounds(lx, ly, ux - lx + 1, uy - ly + 1);

        return r;
    }

    private static int getIndex(int x, int y, int maxX, int maxY) {
        if (x < 0) {
            x = 0;
        }

        if (x >= maxX) {
            x = maxX - 1;
        }

        if (y < 0) {
            y = 0;
        }

        if (y >= maxY) {
            y = maxY - 1;
        }

        return (y * maxX) + x;
    }

    private static synchronized int[] getArray(int size) {
        Integer key = new Integer(size);
        ArrayList list = (ArrayList) intArrays.get(key);

        if ((list == null) || (list.size() == 0)) {
            return new int[size];
        } else {
            return (int[]) list.remove(0);
        }
    }

    private static synchronized void putArray(int[] a) {
        Integer key = new Integer(a.length);
        ArrayList list = (ArrayList) intArrays.get(key);

        if (list == null) {
            list = new ArrayList();
            intArrays.put(key, list);
        }

        list.add(a);
    }

    /**
     * Uses integer box filter to downsample an image two one half its
     * size.  This is used for making excellent mipmaps.
     * @param source
     * @return
     */
    public static BufferedImage downSampleRGB(BufferedImage source) {
        int j;
        final int sourceWidth = source.getWidth();
        final int sourceHeight = source.getHeight();
        final int destWidth = (sourceWidth > 1) ? (sourceWidth / 2) : 1;
        final int destHeight = (sourceHeight > 1) ? (sourceHeight / 2) : 1;

        BufferedImage dest = new BufferedImage(destWidth, destHeight,
                BufferedImage.TYPE_INT_RGB);
        int[] spix = getArray(sourceWidth * sourceHeight);
        int[] dpix = getArray(destWidth * destHeight);
        source.getRGB(0, 0, sourceWidth, sourceHeight, spix, 0, sourceWidth);

        for (j = 0; j < destHeight; j++) {
            int i;

            for (i = 0; i < destWidth; i++) {
                // calculate the pixel location
                int s0 = getIndex(i * 2, j * 2, sourceWidth, sourceHeight);
                int s1 = getIndex((i * 2) + 1, j * 2, sourceWidth, sourceHeight);
                int s2 = getIndex(i * 2, (j * 2) + 1, sourceWidth, sourceHeight);
                int s3 = getIndex((i * 2) + 1, (j * 2) + 1, sourceWidth,
                        sourceHeight);

                int red = ((spix[s0] >> 16) & 0xff) +
                    ((spix[s1] >> 16) & 0xff) + ((spix[s2] >> 16) & 0xff) +
                    ((spix[s3] >> 16) & 0xff);

                int green = ((spix[s0] >> 8) & 0xff) +
                    ((spix[s1] >> 8) & 0xff) + ((spix[s2] >> 8) & 0xff) +
                    ((spix[s3] >> 8) & 0xff);

                int blue = (spix[s0] & 0xff) + (spix[s1] & 0xff) +
                    (spix[s2] & 0xff) + (spix[s3] & 0xff);

                red /= 4;
                green /= 4;
                blue /= 4;

                dpix[i + (destWidth * j)] = ((red << 16) | (green << 8) | blue);
            }
        }

        dest.setRGB(0, 0, destWidth, destHeight, dpix, 0, destWidth);
        putArray(dpix);
        putArray(spix);

        return dest;
    }

    public static BufferedImage downSampleRGBA(BufferedImage source) {
        int j;
        final int sourceWidth = source.getWidth();
        final int sourceHeight = source.getHeight();
        final int destWidth = (sourceWidth > 1) ? (sourceWidth / 2) : 1;
        final int destHeight = (sourceHeight > 1) ? (sourceHeight / 2) : 1;

        BufferedImage dest = new BufferedImage(destWidth, destHeight,
                BufferedImage.TYPE_INT_ARGB);
        int[] spix = getArray(sourceWidth * sourceHeight);
        int[] dpix = getArray(destWidth * destHeight);
        source.getRGB(0, 0, sourceWidth, sourceHeight, spix, 0, sourceWidth);

        for (j = 0; j < destHeight; j++) {
            int i;

            for (i = 0; i < destWidth; i++) {
                // calculate the pixel location
                int s0 = getIndex(i * 2, j * 2, sourceWidth, sourceHeight);
                int s1 = getIndex((i * 2) + 1, j * 2, sourceWidth, sourceHeight);
                int s2 = getIndex(i * 2, (j * 2) + 1, sourceWidth, sourceHeight);
                int s3 = getIndex((i * 2) + 1, (j * 2) + 1, sourceWidth,
                        sourceHeight);

                int alpha = ((spix[s0] >> 24) & 0xff) +
                    ((spix[s1] >> 24) & 0xff) + ((spix[s2] >> 24) & 0xff) +
                    ((spix[s3] >> 24) & 0xff);

                int red = ((spix[s0] >> 16) & 0xff) +
                    ((spix[s1] >> 16) & 0xff) + ((spix[s2] >> 16) & 0xff) +
                    ((spix[s3] >> 16) & 0xff);

                int green = ((spix[s0] >> 8) & 0xff) +
                    ((spix[s1] >> 8) & 0xff) + ((spix[s2] >> 8) & 0xff) +
                    ((spix[s3] >> 8) & 0xff);

                int blue = (spix[s0] & 0xff) + (spix[s1] & 0xff) +
                    (spix[s2] & 0xff) + (spix[s3] & 0xff);

                red /= 4;
                green /= 4;
                blue /= 4;
                alpha /= 4;

                dpix[i + (destWidth * j)] = ((alpha << 24) | (red << 16) |
                    (green << 8) | blue);
            }
        }

        dest.setRGB(0, 0, destWidth, destHeight, dpix, 0, destWidth);

        putArray(dpix);
        putArray(spix);

        return dest;
    }

    /**
     * Takes the buffered image and builds a new one which is centered and scaled.
     * Alpha blended edges are discarded.  A margin is in pixels around the image.
     */
    public static BufferedImage centerAndScale(BufferedImage image, int width,
        int height, int margin) {
        Rectangle r = alphaBounds(image);
        System.out.println("   Alpha bounds : " + (int) r.getWidth() + "+" +
            (int) r.getHeight());
        System.out.println("      min : " + (int) r.getMinX() + "," +
            (int) r.getMinY());
        System.out.println("      max : " + (int) r.getMaxX() + "," +
            (int) r.getMaxY());

        int w;
        int h;
        int lx;
        int ly;

        if (r.getWidth() < r.getHeight()) {
            double scale = r.getWidth() / r.getHeight();
            h = height - (margin * 2);
            w = (int) (h * scale);
        } else {
            double scale = r.getHeight() / r.getWidth();
            w = width - (margin * 2);
            h = (int) (w * scale);
        }

        System.out.println("   New width " + w);
        System.out.println("   New height " + h);

        lx = ((width / 2) - (w / 2));
        ly = ((height / 2) - (h / 2));

        BufferedImage bi = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) bi.getGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image, lx, ly, (lx + w) - 1, (ly + h) - 1,
            (int) r.getMinX(), (int) r.getMinY(), (int) r.getMaxX(),
            (int) r.getMaxY(), null);

        return bi;
    }

    public static BufferedImage readImage(String filename)
        throws IOException {
        File f = new File(filename);

        return ImageIO.read(f);
    }

    public static BufferedImage readImage(String name, Object c)
        throws IOException {
        BufferedImage image = (BufferedImage) images.get(name);

        if (image != null) {
            return image;
        }

        ClassLoader classloader = c.getClass().getClassLoader();
        URL url = classloader.getResource(name);

        if (url == null) {
            throw new IOException("Cannot find file " + name + " on classpath");
        }

        BufferedImage i = ImageIO.read(url);
        images.put(name, i);

        return i;
    }
}
