package io;

import ij.ImageJ;
import ij.ImagePlus;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel3D;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.rstutils.MorphoUtils;

public class TestMorpho {

    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        ImagePlus img = new ImagePlus("/home/rfernandez/Bureau/testUntitled.tif");
        VitimageUtils.printImageResume(img);
        img.show();
        ImagePlus result=dilationSpheroid3D(img, 3, 3, 3);
        result.show();
    }
        public static ImagePlus dilationSpheroid3D(ImagePlus img, int radiusX, int radiusY, int radiusZ) {
        Strel3D str3 = inra.ijpb.morphology.strel.EllipsoidStrel.fromRadiusList(radiusX, radiusY, radiusZ);
        return new ImagePlus("Dilated", Morphology.dilation(img.getImageStack(), str3));
    }
}
