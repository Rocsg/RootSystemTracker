package io.github.rocsg.rstutils;

import ij.IJ;
import ij.ImagePlus;
import inra.ijpb.binary.geodesic.GeodesicDistanceTransformFloat;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel3D;
import io.github.rocsg.fijiyama.common.VitimageUtils;

public class MorphoUtils {

    static float[] floatWeights = new float[]{1000, 1414};

    /// Various helpers for image manipulation //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static ImagePlus erosionLine2D(ImagePlus img, int radius, boolean horizontal) {
        Strel3D str2 = null;
        if (horizontal) str2 = inra.ijpb.morphology.strel.LinearHorizontalStrel.fromRadius(radius);
        else str2 = inra.ijpb.morphology.strel.LinearVerticalStrel.fromRadius(radius);
        return new ImagePlus("", Morphology.erosion(img.getImageStack(), str2));
    }


    public static ImagePlus getDistOut(ImagePlus source, boolean highRes) {
        ImagePlus mask = VitimageUtils.thresholdImage(source, 0.5, 1000000);
        ImagePlus distOut = MorphoUtils.computeGeodesicInsideComponent(mask, 0);
        distOut = VitimageUtils.makeOperationBetweenTwoImages(distOut, distOut, 2, true);
        distOut = VitimageUtils.makeOperationOnOneImage(distOut, 2, highRes ? 0.01 : 0.03, true);
        return distOut;
    }




    public static ImagePlus dilationLine2D(ImagePlus img, int radius, boolean horizontal) {
        Strel3D str2 = null;
        if (horizontal) str2 = inra.ijpb.morphology.strel.LinearHorizontalStrel.fromRadius(radius);
        else str2 = inra.ijpb.morphology.strel.LinearVerticalStrel.fromRadius(radius);
        return new ImagePlus("", Morphology.dilation(img.getImageStack(), str2));
    }

    public static ImagePlus dilationCircle2D(ImagePlus img, int radius) {
        Strel3D str2 = null;
        str2 = inra.ijpb.morphology.strel.DiskStrel.fromRadius(radius);
        return new ImagePlus("", Morphology.dilation(img.getImageStack(), str2));
    }

    public static ImagePlus erosionCircle2D(ImagePlus img, int radius) {
        Strel3D str2 = null;
        str2 = inra.ijpb.morphology.strel.DiskStrel.fromRadius(radius);
        return new ImagePlus("", Morphology.erosion(img.getImageStack(), str2));
    }

    public static ImagePlus computeGeodesic(ImagePlus imgSeed, ImagePlus imgMask, boolean invertDistance) {
        ImagePlus t1 = imgSeed.duplicate();
        if (t1.getType() != ImagePlus.GRAY8) IJ.run(t1, "8-bit", "");
        ImagePlus t2 = imgMask.duplicate();
        if (t2.getType() != ImagePlus.GRAY8) IJ.run(t2, "8-bit", "");
        ImagePlus result = new ImagePlus(
                "geodistance", new GeodesicDistanceTransformFloat(
                floatWeights, false).geodesicDistanceMap(t1.getStack().getProcessor(1), t2.getStack().getProcessor(1)));
        result = noNanInFloat(result, -1);
        if (!invertDistance) return result;
        else {
            double max = VitimageUtils.maxOfImage(result);
            result = VitimageUtils.makeOperationOnOneImage(result, 2, -1, true);
            result = VitimageUtils.makeOperationOnOneImage(result, 1, max, true);

            ImagePlus imgMaskOut = VitimageUtils.getBinaryMaskUnary(t2, 0.5);
            IJ.run(imgMaskOut, "32-bit", "");
            imgMaskOut = VitimageUtils.invertBinaryMask(imgMaskOut);
            imgMaskOut = VitimageUtils.makeOperationOnOneImage(imgMaskOut, 2, -1, true);

            ImagePlus imgMaskIn = VitimageUtils.getBinaryMaskUnary(t2, 0.5);
            imgMaskIn = VitimageUtils.makeOperationBetweenTwoImages(imgMaskIn, result, 2, true);
            result = VitimageUtils.makeOperationBetweenTwoImages(imgMaskOut, imgMaskIn, 1, true);
            result = VitimageUtils.makeOperationOnOneImage(result, 2, 0.001, true);
            return result;
        }
    }


    public static ImagePlus computeGeodesicInsideComponent(ImagePlus imgSeg, double minValue) {
        ImagePlus imgSegWithBorders = VitimageUtils.convertByteToFloatWithoutDynamicChanges(imgSeg);
        imgSegWithBorders = VitimageUtils.uncropImageFloat(imgSegWithBorders, 3, 3, 0, imgSegWithBorders.getWidth() + 6, imgSegWithBorders.getHeight() + 6, 1);
        ImagePlus imgSeed = VitimageUtils.invertBinaryMask(imgSegWithBorders);
        ImagePlus imgSegWithBordersDil = MorphoUtils.dilationCircle2D(imgSegWithBorders, 1);
        imgSeed = VitimageUtils.binaryOperationBetweenTwoImages(imgSeed, imgSegWithBordersDil, 2);
        //VitimageUtils.compositeNoAdjustOf(imgSegWithBordersDil, imgSeed).show();
        IJ.run(imgSeed, "8-bit", "");
        IJ.run(imgSegWithBordersDil, "8-bit", "");
        ImagePlus distance = computeGeodesic(imgSeed, imgSegWithBordersDil, true);
        return VitimageUtils.cropFloatImage(distance, 3, imgSeg.getWidth() + 3 - 1, 3, imgSeg.getHeight() + 3 - 1, 0, 0);
    }


    public static ImagePlus noNanInFloat(ImagePlus imgRef, float replacementValue) {
        ImagePlus img = VitimageUtils.imageCopy(imgRef);
        float[][] in = new float[img.getStackSize()][];
        int X = img.getWidth();
        int Y = img.getHeight();
        int Z = img.getStackSize();
        for (int z = 0; z < Z; z++) {
            in[z] = (float[]) img.getStack().getProcessor(z + 1).getPixels();
            for (int x = 0; x < X; x++) {
                for (int y = 0; y < Y; y++) {
                    if (Float.isNaN((float) (in[z][y * X + x]))) in[z][y * X + x] = replacementValue;
                    if (Float.isInfinite((float) (in[z][y * X + x]))) in[z][y * X + x] = replacementValue;
                }
            }
        }
        return img;
    }


}
