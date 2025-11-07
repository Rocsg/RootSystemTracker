package io.github.rocsg.topologicaltracking;

import ij.ImagePlus;

public class Utils {
    static int sizeFactorForGraphRendering=6;
    public static int minNbCCPerPlant=10;
    public static int minSurfacePerPlant=1000;
    public static int MAX_ORDER_ROOTS=100;
    public static ImagePlus setLowValueTo(ImagePlus img, double minVal, double maxVal, double replacement) {
        ImagePlus ret = img.duplicate();
        float[] tab = (float[]) ret.getStack().getProcessor(1).getPixels();
        int xM = img.getWidth();
        int yM = img.getHeight();
        for (int x = 0; x < xM; x++)
            for (int y = 0; y < yM; y++) {
                if (tab[y * xM + x] >= minVal && tab[y * xM + x] < maxVal) tab[y * xM + x] = (float) replacement;
            }
        return ret;
    }


    public static double distance(double x0,double y0, double x1,double y1){
        return(Math.sqrt((x0-x1)*(x0-x1)+(y0-y1)*(y0-y1)));
    }   

    public static int toInt(double f) {
        return (int) Math.round(f);
    }

    public static int toInt(float f) {
        return Math.round(f);
    }

    public static int toInt(byte b) {
        return (int) (b & 0xff);
    }

    public static int toInt(short b) {
        return (int) (b & 0xffff);
    }

    public static short toShort(int i) {
        if (i > 65535) i = 65535;
        if (i < 0) i = 0;
        return (short) i;
    }   

    public static byte toByte(double d) {
        int i = (int) Math.round(d);
        if (i > 255) i = 255;
        if (i < 0) i = 0;
        return (byte) i;
    }

    public static byte toByte(int i) {
        if (i > 255) i = 255;
        if (i < 0) i = 0;
        return (byte) i;
    }


}
