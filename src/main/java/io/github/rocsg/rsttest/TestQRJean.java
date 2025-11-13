package io.github.rocsg.rsttest;

import static io.github.rocsg.rstplugin.Plugin_RootDatasetMakeInventory.startInventoryOfAMessyDirButAllTheImagesContainQRCodes;
import io.github.rocsg.rstutils.QRcodeReader;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

public class TestQRJean {
    public static void main(String[] args) {
        ImageJ ij=new ImageJ();
        ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/A_Test/RootSystemTracker/JeanTrap/testQR.tif");
        String a=QRcodeReader.decodeQRCode(img);
        System.out.println(a);
        img.show();
//        String dataPath="/home/rfernandez/Bureau/A_Test/RootSystemTracker/JeanTrap/";
//        startInventoryOfAMessyDirButAllTheImagesContainQRCodes(dataPath+"Input", dataPath+"Output");



}
}
