package io.github.rocsg.rstutils;

/**
Based on :
io.github.callicoder/qr-code-generator-and-reader
Rajeev Kumar Singh initial commit
Latest commit acb0fd6 on Aug 22, 2017
 History
 0 contributors
40 lines (35 sloc)  1.37 KB
*/


import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.Timer;
import io.github.rocsg.fijiyama.common.VitimageUtils;

import javax.imageio.ImageIO;

//import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSignedHpsMeasure;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;



public class QRcodeReader {

	/** I run some tests about sensitivity of this reader
	It seems to be robust to pixel size (I handled 4 fold subsampling)
	It seems to be not robust to relative size of QR // image, especially if other structures come there
	*/

	public static String decodeQRCode(ImagePlus img) {
		ImageProcessor ip=img.getProcessor();		
		new MultiFormatReader();
		BufferedImage bufferedImage = ip.getBufferedImage();
        LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            Result result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        } catch (NotFoundException e) {
            //System.out.println("There is no QR code in the image");
            return "";
        }
	}

	
	public static String decodeQRCode(File qrCodeimage) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(qrCodeimage);
        LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            Result result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        } catch (NotFoundException e) {
            System.out.println("There is no QR code in the image");
            return "";
        }
    }

	
    
    @SuppressWarnings("unchecked")
	public static String decodeQRCodeRobust(ImagePlus img2,int subsamplingFactor,double originalQRwidth,double originalX, double originalY,double lowThresh,double highThresh) {
    	String str="";//(img2);
    	if(str.length()!=0)return str;
    	else {
    		int X=img2.getWidth()/subsamplingFactor;
    		int Y=img2.getHeight()/subsamplingFactor;
            ImagePlus img=VitimageUtils.resize(img2, img2.getWidth()/subsamplingFactor, img2.getHeight()/subsamplingFactor, 1);
            ArrayList<Double[]>ar=new ArrayList<Double[]>();
    		int sizeZone=(int) (1.6*originalQRwidth/(subsamplingFactor));
    		double step=sizeZone/9;
    		int deltaThresh=(int) (highThresh-lowThresh);
    		int reducedX=(int) (originalX/subsamplingFactor-sizeZone/2);
    		int reducedY=(int) (originalY/subsamplingFactor-sizeZone/2);
    		int meanThresh=(int) ((lowThresh+highThresh)/2);
    		for(int x0=0; x0<X-sizeZone-1;x0+=step) {
        		for(int y0=0; y0<Y-sizeZone-1;y0+=step) {
        			for(int thresh=(int) lowThresh;thresh<highThresh;thresh+=deltaThresh/8) {
        				double score=VitimageUtils.distance(x0, y0, reducedX, reducedY)*(Math.abs(thresh-meanThresh));
        				ar.add(new Double[] {score,(double) x0,(double) y0,(double) thresh});
        			}
        		}
    		}
    		
    		boolean debug=false;
    		Collections.sort(ar,new DeltaComparator());
    		System.out.println("Looking QR code around "+reducedX+" , "+reducedY+" with thresh around "+meanThresh);
    		Timer t=new Timer();
    		t.getTime();
    		String textGet="";
    		boolean first=true;
    		for(int i=0;i<ar.size() && t.getTime()<100 ;i++) {
    			double x0=ar.get(i)[1];
    			double y0=ar.get(i)[2];
    			double tr=ar.get(i)[3];
    			double score=ar.get(i)[0];
    			ImagePlus imgPti=VitimageUtils.cropImage(img, (int)x0, (int)y0, 0, sizeZone, sizeZone, 1);
    			if(debug && first) {
    				imgPti.show();
    				VitimageUtils.waitFor(500);
        			imgPti.hide();
        			first=false;
    			}
    			imgPti=VitimageUtils.thresholdImage(imgPti, tr, 1E10);
    			textGet=decodeQRCode(imgPti);
    			if(textGet.length()>0) {
    				System.out.println(i+":"+ar.get(i));
    				System.out.println("Found ! Time="+t.getTime()+" , attempt "+i+"/"+ar.size()+" .. around "+x0+" , "+y0+" , "+tr+" with score "+score);
        			System.out.println("Found ! "+textGet);
    				if(debug) {
        			imgPti.show();
    				VitimageUtils.waitFor(500);
        			imgPti.hide();
    				}
    				return textGet;
    			}
    			imgPti.close();
    		}
    	}
    	System.out.println("\n\n NOT FOUND ! \n\n");
    	return "";
    }

    public static void main(String[] args) throws IOException {
    	File file = new File("/home/rfernandez/Bureau/QRCode5Trans.jpg");
		ImagePlus img= IJ.openImage(file.getAbsolutePath());
    	VitimageUtils.waitFor(6000);

		String decodedText = decodeQRCodeRobust(img,4,456,3330,1100,11,160);
		if(decodedText.length()==0) {
		    System.out.println("No QR Code found in the image");
		} else {
		    System.out.println("Decoded text = " + decodedText);
		}
    }    
}
/**
 * The Class VolumeComparator.
 */
@SuppressWarnings("rawtypes")
class DeltaComparator implements java.util.Comparator {
	   
		/**
		 * Compare.
		 *
		 * @param o1 the o 1
		 * @param o2 the o 2
		 * @return the int
		 */
		public int compare(Object o1, Object o2) {
	      return ((Double) ((Object[]) o1)[0]).compareTo((Double)((Object[]) o2)[0]);
	   }
	}

