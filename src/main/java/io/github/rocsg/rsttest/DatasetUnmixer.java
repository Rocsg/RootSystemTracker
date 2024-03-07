package io.github.rocsg.rsttest;

import java.io.File;
import java.util.ArrayList;

import io.github.rocsg.fijiyama.common.VitiDialogs;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;

public class DatasetUnmixer {

	public static void main(String[] args) {
		ImageJ ij = new ImageJ();
		// boites à 7+ : 24 8 35 37 28
		// coord1();
		// coord2();
		// coord3();
		// corrections();
		// coord4();
		coord5();
	}

	public static String stringBoite(int a) {
		return (a < 10000 ? "0" : "") + (a < 1000 ? "0" : "") + (a < 100 ? "0" : "") + (a < 10 ? "0" : "") + a;
	}

	public static void coord5() {
		int predMiss = 0;
		int measMiss = 0;
		int numBoite = 1;
		// Frac4Date24
		// 3548 est copie de 3764
		System.out.println("4-24");
		int[][] seriesFrac4Date24 = new int[][] {
				{ 7, 14, 21, 28, 34 /* 7 redite */, 36 },
				{ 10, 17, 24, 30, 2 },
				{ 12, 19, 26, 32, 3 },
				{ 9, 16, 23, 4, 35 },
				{ 11, 18, 25, 31, 5 },
				{ 6, 13, 20, 27, 33 },
				{ 8, 15, 22, 29, 37 }
		};
		measMiss = verify(37, seriesFrac4Date24);
		predMiss = 1;
		ImagePlus img = IJ.openImage("/home/rfernandez/Bureau/Temp/NoFuckRootNav/Frac=4-Date=24/v1.tif");
		System.out.println("Assert equality est/meas on the missed ? " + (measMiss == predMiss));
		numBoite = stackBoites(seriesFrac4Date24, numBoite, img);

		// 3526 est copie de 2576
		// 2579 est copie de 3529
		System.out.println("4-12");
		int[][] seriesFrac4Date12 = new int[][] {
				{ 10, 24, 31, 3, 43, 50, 7, 9 },
				{ 15, 11, 27, 34, 40, 46 },
				{ 13, 19, 25, 32, 38, 44, 8 },
				{ 16, 21, 28, 35, 41, 47, 5 },
				{ 17, 22, 29, 36, 42, 48, 6 },
				{ 14, 20, 26, 33, 39, 45, 4 },
				{ 18, 23, 30, 37, 49 }
		};
		measMiss = verify(50, seriesFrac4Date12);
		predMiss = 3;
		img = IJ.openImage("/home/rfernandez/Bureau/Temp/NoFuckRootNav/Frac=4-Date=12/v1.tif");
		System.out.println("Assert equality est/meas on the missed ? " + (measMiss == predMiss));
		numBoite = stackBoites(seriesFrac4Date12, numBoite, img);

		// 3534 et 3585, 11 eliminated
		// 3590 et 3539, 12 eliminated
		// 3546 et 3761, 41 eliminated
		// 3545 et 3595, 14 eliminated
		// 3547 et 3762, 42 eliminated

		System.out.println("2-24");
		int[][] seriesFrac2Date24 = new int[][] {
				{ 16, 23, 1, 2, 38, 8, 43 },
				{ 21, 27, 33, 3, 6 },
				{ 18, 25, 30, 35, 4, 15 },
				{ 20, 26, 32, 37, 5 },
				{ 22, 28, 34, 7, 9 },
				{ 19, 10, 31, 36, 40 },
				{ 17, 24, 29, 13, 39 }
		};
		measMiss = verify(43, seriesFrac2Date24);
		predMiss = 5;
		img = IJ.openImage("/home/rfernandez/Bureau/Temp/NoFuckRootNav/Frac=2-Date=24/v1.tif");
		System.out.println("Assert equality est/meas on the missed ? " + (measMiss == predMiss));
		numBoite = stackBoites(seriesFrac2Date24, numBoite, img);

		// 3528 et 3578, 11 eliminated
		// 3586 et 3535, 2 eliminated
		System.out.println("2-12");
		int[][] seriesFrac2Date12 = new int[][] {
				{ 14, 1, 27, 33, 40, 46 },
				{ 19, 25, 12, 38, 44, 51, 7 },
				{ 18, 24, 31, 37, 43, 50, 3, 6, 9 },
				{ 16, 22, 29, 35, 42, 48 },
				{ 17, 23, 30, 36, 13, 49, 5, 8 },
				{ 15, 21, 28, 34, 41, 47 },
				{ 20, 26, 32, 39, 45, 52, 4, 10 }
		};
		measMiss = verify(52, seriesFrac2Date12);
		predMiss = 2;
		img = IJ.openImage("/home/rfernandez/Bureau/Temp/NoFuckRootNav/Frac=2-Date=12/v1.tif");
		System.out.println("Assert equality est/meas on the missed ? " + (measMiss == predMiss));
		numBoite = stackBoites(seriesFrac2Date12, numBoite, img);

		// 3530 et 3580, 14 eliminated
		// 3581 et 3531, 15
		// 3538 et 3589, 19
		// 3543 et 3755, 42
		// 3583 et 3532, 16
		// 3541 et 3752, 39
		// 3584 et 3533, 17
		// 3537 et 3588, 18
		// 3542 et 3753, 40
		System.out.println("1-24");
		int[][] seriesFrac1Date24 = new int[][] {
				{ 24, 1, 32, 37, 41, 12, 46 },
				{ 25, 2, 33, 6, 9, 13 },
				{ 21, 27, 3, 35, 7, 10, 44 },
				{ 23, 29, 4, 5, 8, 11, 45 },
				{ 22, 28, 31, 36, 20 },
				{ 26, 30, 34, 38, 43 },
		};
		measMiss = verify(46, seriesFrac1Date24);
		predMiss = 9;
		img = IJ.openImage("/home/rfernandez/Bureau/Temp/NoFuckRootNav/Frac=1-Date=24/v1.tif");
		System.out.println("Assert equality est/meas on the missed ? " + (measMiss == predMiss));
		numBoite = stackBoites(seriesFrac1Date24, numBoite, img);

		// 3577 et 3527, 7
		System.out.println("1-12");
		int[][] seriesFrac1Date12 = new int[][] {
				{ 12, 17, 23, 29, 35, 39, 2, 5 },
				{ 1, 19, 25, 31, 8, 41, 3 },
				{ 14, 20, 26, 32, 37, 42, 4, 6 },
				{ 11, 16, 22, 28, 34, 9 },
				{ 13, 18, 24, 30, 36, 40 },
				{ 10, 15, 21, 27, 33, 38 },
		};
		measMiss = verify(42, seriesFrac1Date12);
		predMiss = 1;
		img = IJ.openImage("/home/rfernandez/Bureau/Temp/NoFuckRootNav/Frac=1-Date=12/v1.tif");
		System.out.println("Assert equality est/meas on the missed ? " + (measMiss == predMiss));
		numBoite = stackBoites(seriesFrac1Date12, numBoite, img);

	}

	public static int stackBoites(int[][] series, int numBoite, ImagePlus img) {
		for (int s = 0; s < series.length; s++) {
			ImagePlus[] tab = new ImagePlus[series[s].length];
			for (int i = 0; i < tab.length; i++) {
				int numSli = series[s][i];
				int numReal = Integer.parseInt(img.getStack().getSliceLabel(numSli).split("-")[0].split("=")[1]);
				tab[i] = IJ.openImage("/media/rfernandez/DATA_RO_A/Roots_systems/Data_Rootnav/" + numReal + "/image_"
						+ numReal + ".tif");
			}
			ImagePlus result = VitimageUtils.slicesToStack(tab);
			IJ.run(result, "Invert", "");
			IJ.saveAsTiff(result,
					"/home/rfernandez/Bureau/A_Test/RSML/0_Stacked/MLRootNav_Boite_" + stringBoite(numBoite) + ".tif");
			numBoite++;
		}
		return numBoite;
	}

	public static int verify(int last, int[][] serie) {
		int ret = 0;
		int[] iters = new int[last + 1];
		for (int i = 0; i < serie.length; i++)
			for (int j = 0; j < serie[i].length; j++) {
				iters[serie[i][j]]++;
			}
		for (int i = 1; i < last + 1; i++) {
			if (iters[i] != 1) {
				ret++;
				System.out.println("Warning : i=" + i + " had " + iters[i] + " iterations");
			}
			// System.out.println("I="+i+" : "+iters[i]);
		}
		return ret;
	}

	// 3565 ??? entre deux et 4

	public static void corrections() {
		String[][] listeCorr = new String[][] {
				{ "N=3534", "Frac=4", "Frac=2" },
				{ "N=3590", "Frac=4", "Frac=2" },
		};

		ImagePlus img = IJ.openImage("/home/rfernandez/Bureau/Temp/NoFuckRootNav/testRoot4.tif");
		int N = img.getStackSize();

		for (int i = 0; i < listeCorr.length; i++) {
			String fetch = listeCorr[i][0];
			String oldS = listeCorr[i][1];
			String newS = listeCorr[i][2];

			for (int n = 0; n < N; n++) {
				String label = img.getStack().getSliceLabel(n + 1);
				if (label.contains(fetch)) {
					String label2 = label.replace(oldS, newS);
					img.getStack().setSliceLabel(label2, n + 1);
				}
			}
		}
		IJ.saveAsTiff(img, "/home/rfernandez/Bureau/Temp/NoFuckRootNav/testRoot5.tif");

	}

	public static void coord4() {

		ImagePlus img = IJ.openImage("/home/rfernandez/Bureau/Temp/NoFuckRootNav/testRoot5.tif");
		int N = img.getStackSize();
		for (String s : new String[] {
				"Frac=4-Date=12",
				"Frac=4-Date=24",
				"Frac=2-Date=12",
				"Frac=2-Date=24",
				"Frac=1-Date=12",
				"Frac=1-Date=24",
		}) {
			System.out.println("Processing " + s);
			ArrayList<ImagePlus> ar = new ArrayList<ImagePlus>();
			for (int i = 0; i < N; i++) {
				String label = img.getStack().getSliceLabel(i + 1);
				if (label.contains(s)) {
					ImagePlus im = new Duplicator().run(img, 1, 1, i + 1, i + 1, 1, 1);
					ar.add(im);
				}
			}
			ImagePlus[] tab = new ImagePlus[ar.size()];
			for (int i = 0; i < ar.size(); i++)
				tab[i] = ar.get(i);
			ImagePlus it = VitimageUtils.slicesToStack(tab);
			new File("/home/rfernandez/Bureau/Temp/NoFuckRootNav/" + s).mkdirs();
			IJ.saveAsTiff(it, "/home/rfernandez/Bureau/Temp/NoFuckRootNav/" + s + "/v1.tif");
		}
	}

	public static void coord3() {
		ImagePlus img = IJ.openImage("/home/rfernandez/Bureau/NoFuckRootNav/testRoot3.tif");
		ImagePlus img2 = img.duplicate();
		String base3 = "/home/rfernandez/Bureau/NoFuckRootNav/testRoot4.tif";
		int N = img.getStackSize();
		int baseN = 3526;
		int baseSl = 1;
		for (int i = 0; i < N; i++) {
			String label = img2.getStack().getSliceLabel(i + 1);
			int val1 = (int) Double.parseDouble(label.split("-")[0]);
			int val2 = (int) Double.parseDouble(label.split("-")[1]);
			String label2 = "N=" + (baseN + i) + "-Sl=" + (baseSl + i) + "-Frac=";
			if (val1 < 125)
				label2 += "4";
			else if (val1 > 275)
				label2 += "1";
			else
				label2 += "2";

			label2 += "-Date=";
			if (val2 < 200)
				label2 += "12";
			else
				label2 += "24";
			System.out.println(i);
			System.out.println("Vals=" + val1 + " , " + val2);
			img2.getStack().setSliceLabel(label2, i + 1);
		}
		IJ.saveAsTiff(img2, base3);
	}

	public static void coord2() {
		ImagePlus img = IJ.openImage("/home/rfernandez/Bureau/NoFuckRootNav/testRoot3.tif");
		ImagePlus img2 = img.duplicate();
		String base3 = "/home/rfernandez/Bureau/NoFuckRootNav/testRoot3.tif";
		int N = img.getStackSize();
		for (int i = 36; i < N; i++) {
			System.out.println("\n" + i + "\n");
			ImagePlus im = new Duplicator().run(img, 1, 1, i + 1, i + 1, 1, 1);
			im = VitimageUtils.cropImage(im, 300, 0, 0, 300, 200, 1);
			im = VitimageUtils.resize(im, 600, 400, 1);
			im.show();
			double[][] tab = VitiDialogs.waitForPointsUI(1, im, false);
			im.close();
			String label = img2.getStack().getSliceLabel(i + 1) + tab[0][1] + "-";
			img2.getStack().setSliceLabel(label, i + 1);
			if ((i % 5) == 0)
				IJ.saveAsTiff(img2, base3);
		}
		IJ.saveAsTiff(img2, base3);
	}

	public static void coord1() {
		ImagePlus img = IJ.openImage("/home/rfernandez/Bureau/NoFuckRootNav/testRoot2.tif");
		ImagePlus img2 = img.duplicate();
		String base2 = "/home/rfernandez/Bureau/NoFuckRootNav/testRoot2.tif";
		int N = img.getStackSize();
		for (int i = 266; i < N; i++) {
			ImagePlus im = new Duplicator().run(img, 1, 1, i + 1, i + 1, 1, 1);
			im = VitimageUtils.cropImage(im, 500, 0, 0, 300, 200, 1);
			im = VitimageUtils.resize(im, 600, 400, 1);
			im.show();
			double[][] tab = VitiDialogs.waitForPointsUI(1, im, false);
			im.close();
			String label = "" + tab[0][1] + "-";
			img2.getStack().setSliceLabel(label, i + 1);
			if ((i % 5) == 0)
				IJ.saveAsTiff(img2, base2);
		}
		IJ.saveAsTiff(img2, base2);
	}

}
