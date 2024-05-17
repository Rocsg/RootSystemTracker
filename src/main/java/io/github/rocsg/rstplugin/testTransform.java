package io.github.rocsg.rstplugin;

import ij.IJ;
import ij.ImagePlus;
import io.github.rocsg.fijiyama.registration.ItkTransform;

public class testTransform {

    public static void main(String[] args) {
        ItkTransform itkTransform = ItkTransform.readTransformFromFile("D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Process\\B73_R12_01\\Transforms_2\\transform_9.transform.tif");

        ImagePlus imagePlus = IJ.openImage("D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Process\\B73_R12_01\\Transforms_2\\transform_9.transform.tif");

        ImagePlus tr = itkTransform.viewAsGrid3D(imagePlus, 10);
        tr.show();
    }
}
