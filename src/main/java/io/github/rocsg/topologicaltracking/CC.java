   
package io.github.rocsg.topologicaltracking;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.Bord;
import io.github.rocsg.fijiyama.common.Pix;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.registration.TransformUtils;
import io.github.rocsg.rstutils.MorphoUtils;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CC stands for connexe conections. It is a class that represents a connected component in the context of the
 * topological tracking of roots. It is used to represent the nodes of the graph that is used to represent the
 * connections between the connected components of the roots.

 */
public class CC implements Serializable {
    private static final long serialVersionUID = 1L;
    public SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph;
    public static double ratioFuiteBordSurLongueur = 1;
    public int nPixels;
    public int day; //TODO : to change the name to timestep
    public int n; //TODO : to change the name to componentLabel
    public double hour;//TODO : is it used ?
    public double hourGuessedOfStart;
    public double hourGuessedOfCentroid;
    public double hourGuessedOfTip;
    public transient ImagePlus thisSeg = null;//TODO : set a more explicit name
    private transient ImagePlus thisDates;
    public SimpleWeightedGraph<Pix, Bord> pixGraph;

    public double xCentralPixRelative;
    public double yCentralPixRelative;
    public double xCentralPixAbsolu;
    public double yCentralPixAbsolu;
    public int xMin;
    public int yMin;
    public int xMax;
    public int yMax;
    public int width;
    public int height;

    public int nbPixSuccessors=0;
    public int nbSuccessors=0;
    public int indexOfCorrespondingPlant=-1;
    public ArrayList <Integer> indexOfCorrespondingOrganRelativeToParentStructure=new ArrayList<>();
    public int indexInOrgan=-1;
    public int order=0;

    public List<Pix> mainDjikstraPath;
    public List<List<Pix>> secondaryDjikstraPath;
    public ArrayList<CC> secondaryPathLookup;
    public double deltaTimeHoursBefore;
    public double deltaTimeHoursFromStart;
    private double xCentroidRelative;
    private double yCentroidRelative;
    private double xCentroidAbsolu;
    private double yCentroidAbsolu;

    //Flags
    public CC primaryOfAnEmergingRootAtLastTimeOrFuzzyRegistration = null;
    public double[] facetsMakingItAnEmergingRootAtLastTimeOrFuzzyRegistration;
    public boolean isArtifactIncludedIntoAnotherCCWithoutTouchingTheBorders = false;
    public boolean isArtifactMakingBelieveOfAnOrganStartButIsVerySmallRelativelyToSuccessor=false;



    //En petit questionnement
    public CC incidentCC = null;
    public int count = 0;
    public CC lastCCinLat = null;
    public boolean finalRS = false;
    public boolean finalRoot = false;
    public boolean isStart=false;
    public boolean isEnd=false;
    public boolean isPrimStart = false;
    public boolean isPrimEnd = false;
    public boolean isLatStart = false;
    public boolean isLatEnd = false;
    public boolean isLateral = false;
    public CC associatePrev = null;
    public CC associateSuiv = null;
    public boolean changedRecently = false;
    public int deltaTimeFromStart = 0;
    public int deltaTimeBefore = 0;
    public double lengthFromStart = 0;
    public double lengthBefore = 0;
    public int surfaceFromStart = 0;
    public boolean nonValidLatStart = false;
    public boolean trunk = false;
    public int goesToTheLeft = 0;
    public CC ccPrev = null;
    public CC ccLateralStart = null;
    public ArrayList<CC> pathFromStart = null;


    //En gros questionnement
    public boolean isOut = false;
    public int lateralStamp = -1;
    public int stamp = 0;
    public int stamp2 = 0;
    public double stampDist = 0;
    public boolean illConnected = false;
    public boolean isOutlierTooSmall=false;
    
    // Step 6 : stunning detection
    public int stunningLevel = 0;
    public double estimatedRadius = 0;
    public double estimatedSpeed = 0;
    
    // Détails des problèmes détectés (pour debugging et résolution)
    public ArrayList<String> stunningReasons = new ArrayList<>(); // Types de problèmes détectés
    public ArrayList<Double> stunningMADValues = new ArrayList<>(); // Valeurs de MAD associées (si applicable)
    
    // Step 7 : somme du stunning dans le voisinage direct
    public int stunningLevelSumNeighbours = 0;
    


/******************************************************************************************************************************************************************************************************
 * Constructors and setup methods
 */

    public static CC fuseListOfCCIntoSingleCC(List<CC> list) {
        int nCC = list.size();
        CC ccFuse = new CC();

        int fusXMin=1000000;
        int fusXMax=0;
        int fusYMin=1000000;
        int fusYMax=0;
        for(CC cc:list) if(cc.xMin<fusXMin) fusXMin=cc.xMin;
        for(CC cc:list) if(cc.xMax>fusXMax) fusXMax=cc.xMax;
        for(CC cc:list) if(cc.yMin<fusYMin) fusYMin=cc.yMin;
        for(CC cc:list) if(cc.yMax>fusYMax) fusYMax=cc.yMax;
        ccFuse.setupBoundingBox(fusXMin, fusYMin, fusXMax, fusYMax);

        int fusNpix=0;
        for(CC cc:list) fusNpix+=cc.nPixels;
        ccFuse.nPixels=fusNpix;



        ImagePlus fusImgSeg = ij.gui.NewImage.createImage("", ccFuse.width, ccFuse.height, 1, 8,ij.gui.NewImage.FILL_BLACK);
        //Pour chaque CC, coller son thisSeg dans imgSeg, à la bonne place (en décalant de (cc.xMin-fusXMin,cc.yMin-fusYMin) ). Le faire en utilisant le tableau de bytes
        byte[] tabDataDest = (byte[])fusImgSeg.getStack().getPixels(1);
        for(CC cc:list){
            byte[] tabDataSrc = (byte[])cc.thisSeg.getStack().getPixels(1);
            for(int x=0;x<cc.width;x++){
                for(int y=0;y<cc.height;y++){
                    if(Utils.toInt(tabDataSrc[y*cc.width+x])!=0) tabDataDest[(y+cc.yMin-fusYMin)*ccFuse.width+(x+cc.xMin-fusXMin)]=tabDataSrc[y*cc.width+x];
                }
            }
        }
        ccFuse.thisSeg=fusImgSeg;
        ccFuse.thisDates=ccFuse.thisSeg;

        ImagePlus dist = MorphoUtils.computeGeodesicInsideComponent(ccFuse.thisSeg, 0.1);
        double val = VitimageUtils.maxOfImage(dist);
        dist = VitimageUtils.makeOperationOnOneImage(dist, 2, ratioFuiteBordSurLongueur / val, true);
        dist = VitimageUtils.makeOperationOnOneImage(dist, 1, 1, true);

        ccFuse.buildConnectionGraphOfComponent(ccFuse.thisSeg, dist, 8);
        ccFuse.setupCentroid();
        ccFuse.identifyCentralPixel();

        ccFuse.day = -1;
        ccFuse.n = -1;
        ccFuse.graph = list.get(0).graph;
        ccFuse.secondaryDjikstraPath = new ArrayList<List<Pix>>();
        ccFuse.secondaryPathLookup = new ArrayList<CC>();
        ccFuse.hour = -1;
        ccFuse.hourGuessedOfStart = -1;
        ccFuse.hourGuessedOfCentroid = -1;
        ccFuse.hourGuessedOfTip = -1;

        return ccFuse;
    }


    public CC(int xMin, int yMin, int xMax, int yMax, int day, double[] hours, int n, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, ImagePlus imgTotal, ImagePlus imgDatesInit) {
        this.setupBoundingBox(xMin, yMin, xMax, yMax);
        this.setupCCWithImgsAndPixelGraph(day, hours, n, graph, imgTotal, imgDatesInit);
    }


 //If called alone, then necessary to call the second ones successively
    public CC(){
    }

    public void setupBoundingBox(int xMin, int yMin, int xMax, int yMax){
        this.xMin=xMin;
        this.yMin=yMin;
        this.xMax=xMax;
        this.yMax=yMax;
        this.width=xMax-xMin+1;
        this.height=yMax-yMin+1;
    }

    public void setupCCWithImgsAndPixelGraph(int day, double[]hours, int n, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,ImagePlus imgTotalCC,ImagePlus imgDatesInit) {
        this.day = day;
        this.n = n;
        setupThisImages(imgTotalCC,imgDatesInit);
        ImagePlus dist= MorphoUtils.computeGeodesicInsideComponent(this.thisSeg, 0.1);
        double val=VitimageUtils.maxOfImage(dist);
        dist=VitimageUtils.makeOperationOnOneImage(dist, 2, ratioFuiteBordSurLongueur/val, true);
        dist=VitimageUtils.makeOperationOnOneImage(dist, 1, 1, true);
        buildConnectionGraphOfComponent(this.thisSeg, dist, 8);
        boolean debug=false &&(this.day==3 && this.n==5);
        if(debug)System.out.println("Toto start");
        verifySanityOfNonTotalInclusionIntoAnotherCCWithoutTouchingTheBorders(imgTotalCC,imgDatesInit);
        if(debug){
            System.out.println("Toto end");
            System.out.println("Debug info for CC "+this.toString()+":");
            System.out.println(" - Included in another CC without touching borders: "+this.isArtifactIncludedIntoAnotherCCWithoutTouchingTheBorders);
            VitimageUtils.waitFor(50000000);
        }
        setupCentroid();
        identifyCentralPixel();

        this.secondaryDjikstraPath = new ArrayList<List<Pix>>();
        this.secondaryPathLookup = new ArrayList<CC>();
        this.hour = hours[0];
        this.hourGuessedOfStart=hours[1];
        this.hourGuessedOfCentroid=hours[2];
        this.hourGuessedOfTip=hours[3];
        this.graph = graph;        
    }

    public void verifySanityOfNonTotalInclusionIntoAnotherCCWithoutTouchingTheBorders(ImagePlus imgTotalCC, ImagePlus imgDatesInit){
        boolean debug=false &&(this.day==3 && this.n==5);
        //If the image is touching the borders, the CC can come from elsewhere, thus return
        if(this.xMin==0 || this.yMin==0 || this.xMax==imgTotalCC.getWidth()-1 || this.yMax==imgTotalCC.getHeight()-1) return;

        ImagePlus imgMask=VitimageUtils.uncropImageByte(this.thisSeg, 1,1, 0,this.width+2, this.height+2,1);
        ImagePlus imgLabDates=VitimageUtils.cropImage(imgDatesInit, this.xMin-1, this.yMin-1, 0,this.width+2, this.height+2,1);
        // Check if the current CC is included in another CC without touching the borders
        int firstNeighborLabel=-1;

        if(debug){
            VitimageUtils.printImageResume(thisSeg,"thisSeg");
            VitimageUtils.printImageResume(thisDates,"thisDates");


        VitimageUtils.printImageResume(imgMask,"imgMask");
        VitimageUtils.printImageResume(imgLabDates,"imgLabDates");
        }
        //For each pixel of the subimage, check for a possible 4-connexity neighbor. 
        //If 0 found, it is not included, thus return
        //If a value found, if firstNeighborLabel==-1, set it to this value
        //If a value found and firstNeighborLabel!=-1, if it is different from firstNeighborLabel, return
        //At the end of the loop if firstNeighborLabel!=-1, it is included in another CC, thus set the flag
        //The input image is of type short
        byte[] tabDataMask = (byte[])imgMask.getStack().getPixels(1);
        short[] tabDataDates = (short[])imgLabDates.getStack().getPixels(1);
        int index=0;

        for(int x=1;x<=this.width;x++){ 
            for(int y=1;y<=this.height;y++){
                if(debug)System.out.println("Toto pixel "+x+","+y);
                if(Utils.toInt(tabDataMask[y*(this.width+2)+x])==0) continue; //Not under test

                 //Left
                index=(this.width+2)*y+x-1;
                if(Utils.toInt(tabDataMask[index])==0){//Possible neighbour
                    if(Utils.toInt(tabDataDates[index])==0) return; //The CC is not included, as it is touching the background
                    if(firstNeighborLabel==-1) firstNeighborLabel=Utils.toInt(tabDataDates[index]);
                    else if(firstNeighborLabel!=Utils.toInt(tabDataDates[index])) return;//The CC is not included, as it is touching two different CC
                }

                //Right
                index=(this.width+2)*y+x+1;
                if(debug)System.out.println("Toto index right="+index+" / value="+Utils.toInt(tabDataMask[index])+" / date="+Utils.toInt(tabDataDates[index])); 
                if(Utils.toInt(tabDataMask[index])==0){//Possible neighbour
                    if(debug)System.out.println("So entering1");
                    if(Utils.toInt(tabDataDates[index])==0) return; //The CC is not included, as it is touching the background
                    if(debug)System.out.println("So entering2");
                    if(firstNeighborLabel==-1) firstNeighborLabel=Utils.toInt(tabDataDates[index]);
                    if(debug)System.out.println("So entering3");
                    else if(firstNeighborLabel!=Utils.toInt(tabDataDates[index])) return;//The CC is not included, as it is touching two different CC
                    if(debug)System.out.println("So entering4");
                }

                //Up
                index=(this.width+2)*(y-1)+x;
                if(Utils.toInt(tabDataMask[index])==0){//Possible neighbour
                    if(Utils.toInt(tabDataDates[index])==0) return; //The CC is not included, as it is touching the background
                    if(firstNeighborLabel==-1) firstNeighborLabel=Utils.toInt(tabDataDates[index]);
                    else if(firstNeighborLabel!=Utils.toInt(tabDataDates[index])) return;//The CC is not included, as it is touching two different CC
                }

                //Down
                index=(this.width+2)*(y+1)+x;
                if(Utils.toInt(tabDataMask[index])==0){//Possible neighbour
                    if(Utils.toInt(tabDataDates[index])==0) return; //The CC is not included, as it is touching the background
                    if(firstNeighborLabel==-1) firstNeighborLabel=Utils.toInt(tabDataDates[index]);
                    else if(firstNeighborLabel!=Utils.toInt(tabDataDates[index])) return;//The CC is not included, as it is touching two different CC
                }
            }
        }
        if(firstNeighborLabel!=-1){
            this.isArtifactIncludedIntoAnotherCCWithoutTouchingTheBorders=true;
        }
        
    }

    public void setupCentroid(){
        //Read thisSeg, and make an average of x and y for all pixels that are not 0
        //Read the image using the byte array
        byte[] tabDataMask = (byte[])this.thisSeg.getStack().getPixels(1);
        int sumX=0;
        int sumY=0;
        int count=0;
        for(int i=0;i<tabDataMask.length;i++){
            if(tabDataMask[i]!=0){
                sumX+=i%this.width;
                sumY+=i/this.width;
                count++;
            }
        }
        if(count>0){
            this.xCentroidRelative=sumX/count;
            this.yCentroidRelative=sumY/count;
            this.xCentroidAbsolu=this.xMin+this.xCentroidRelative;
            this.yCentroidAbsolu=this.yMin+this.yCentroidRelative;
        }
    }

    public void identifyCentralPixel(){
        double distMin=10E16;
        Pix bestPix=null;
        double meanX=0;
        double meanY=0;
        int count=0;
        for (Pix p : pixGraph.vertexSet()) {
            count++;
            meanX+=p.x;
            meanY+=p.y;
        
            double dx=p.x-this.xCentroidRelative;
            double dy=p.y-this.yCentroidRelative;
            double dist=dx*dx+dy*dy;
            if(dist<distMin){
                distMin=dist;
                bestPix=p;
            }
        }
        meanX/=count;
        meanY/=count;
        if(getPix((int)Math.ceil(meanX), (int)Math.ceil(meanY))!=null && getPix((int)Math.floor(meanX), (int)Math.floor(meanY))!=null && getPix((int)Math.ceil(meanX), (int)Math.floor(meanY))!=null && getPix((int)Math.floor(meanX), (int)Math.ceil(meanY))!=null){
            this.xCentralPixRelative=meanX;
            this.yCentralPixRelative=meanY;
            this.xCentralPixAbsolu=meanX+this.xMin;
            this.yCentralPixAbsolu=meanY+this.yMin;
        }
        else{
            this.xCentralPixRelative=bestPix.x;
            this.yCentralPixRelative=bestPix.y;
            this.xCentralPixAbsolu=bestPix.x+this.xMin;
            this.yCentralPixAbsolu=bestPix.y+this.yMin;
        }
    }

    public void setupThisImages(ImagePlus imgLabelComposantes,ImagePlus imgDatesInit){
        ImagePlus imgLabComp=VitimageUtils.cropImage(imgLabelComposantes, this.xMin, this.yMin, 0,this.width, this.height,1);
        ImagePlus imgOut=imgLabComp.duplicate();
        VitimageUtils.convertToGray8(imgOut);
        //Set to 0 all pixels that have not the label this.n+1
        short[] tabDataShortIn = (short[]) imgLabComp.getStack().getPixels(1);
        byte[] tabDataByteOut = (byte[]) imgOut.getStack().getPixels(1);
        int countPix=0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (Utils.toInt(tabDataShortIn[y*this.width+x]) == this.n + 1){ 
                    countPix++; 
                    tabDataByteOut[y*this.width+x] = Utils.toByte(255);
                }
                else{
                    tabDataByteOut[y*this.width+x] = Utils.toByte(0);
                }
            }
        }
        this.thisSeg = imgOut;
        this.nPixels=countPix;

        ImagePlus imgDates=VitimageUtils.cropImage(imgDatesInit, this.xMin, this.yMin, 0,this.width, this.height,1);
        this.thisDates = imgDates;
    }

  





/******************************************************************************************************************************************************************************************************
 * Accessors and useful methods
 */
    public Pix getPix(int x, int y) {
        for (Pix p : pixGraph.vertexSet()) {
            if (p.x == x && p.y == y) return p;
        }
        return null;
    }

    public double euclidianDistanceToCCCentroid(CC cc2) {
        return VitimageUtils.distance(this.xCentroidAbsolu, this.yCentroidAbsolu, cc2.xCentroidAbsolu, cc2.yCentroidAbsolu);
    }

    public double euclidianDistanceToCCCentralPix(CC cc2) {
        return VitimageUtils.distance(this.xCentralPixAbsolu, this.yCentralPixAbsolu, cc2.xCentralPixAbsolu, cc2.yCentralPixAbsolu);
    }   

    public String toString() {
        return "CC  day." + day + " label." + n + " : " + VitimageUtils.dou(xCentralPixAbsolu* Utils.sizeFactorForGraphRendering) + "," + VitimageUtils.dou(yCentralPixAbsolu* Utils.sizeFactorForGraphRendering ) + 
        " (" + VitimageUtils.dou(xCentralPixAbsolu ) + " - " + VitimageUtils.dou(yCentralPixAbsolu) + 
        ") "+" hStart=" + VitimageUtils.dou(hourGuessedOfStart) + " hCentroid=" + VitimageUtils.dou(hourGuessedOfCentroid) + " hTip=" + VitimageUtils.dou(hourGuessedOfTip) +
        " nPix=" + nPixels + " size=" + width + "x" + height;
    }

    public double xMin() {
        return xMin;
    }

    public double yMin() {
        return yMin;
    }

    public double x() {
        return xCentralPixAbsolu;
    }
   
    public double y() {
        return yCentralPixAbsolu;
    }








/******************************************************************************************************************************************************************************************************
 * Methods for determining facet and build the PixGraph
 */
    public static double[] determineFacet(int[] sNext, int[] tCur, CC ccNext, CC ccCur) {
    double xTar = sNext[0] + ccNext.xMin();
    double yTar = sNext[1] + ccNext.yMin();
    double xSou = tCur[0] + ccCur.xMin();
    double ySou = tCur[1] + ccCur.yMin();
        double[] vectNorm = TransformUtils.normalize(new double[]{xTar - xSou, yTar - ySou, 0});
        if (vectNorm[0] > 0.707) return new double[]{sNext[0] - 0.5, sNext[1]};
        if (vectNorm[0] < (-0.707)) return new double[]{sNext[0] + 0.5, sNext[1]};
        if (vectNorm[1] > 0.707) return new double[]{sNext[0], sNext[1] - 0.5};
        if (vectNorm[1] < (-0.707)) return new double[]{sNext[0], sNext[1] + 0.5};
        if (vectNorm[0] > 0 && vectNorm[1] > 0) return new double[]{sNext[0] - 0.5, sNext[1] - 0.5};
        if (vectNorm[0] > 0 && vectNorm[1] < 0) return new double[]{sNext[0] - 0.5, sNext[1] + 0.5};
        if (vectNorm[0] < 0 && vectNorm[1] > 0) return new double[]{sNext[0] + 0.5, sNext[1] - 0.5};
        if (vectNorm[0] < 0 && vectNorm[1] < 0) return new double[]{sNext[0] + 0.5, sNext[1] + 0.5};
        return null;
    }

    public double[] nFacets4connexe_V1(CC cc2) {
        if (!isPossibleNeighbour(cc2, false)) return new double[]{0, 0, 0};
        int x1 = this.xMin;
        int x2 = cc2.xMin;
        int X1 = x1 + this.width;
        int X2 = x2 + cc2.width;
        int y1 = this.yMin;
        int y2 = cc2.yMin;
        int Y1 = y1 + this.height;
        int Y2 = y2 + cc2.height;
        int nF = 0;
        double xSum = 0;
        double ySum = 0;
        if (this.width * this.height < cc2.width * cc2.height) {
            int xx = x1;
            int XX = X1;
            int yy = y1;
            int YY = Y1;
            for (int x = xx; x <= XX; x++) {
                for (int y = yy; y <= YY; y++) {
                    if (!this.containsPixOfCoordinateAbsolute(x, y)) continue;
                    if (cc2.containsPixOfCoordinateAbsolute(x + 1, y)) {
                        xSum += (x + 0.5);
                        ySum += y;
                        nF++;
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x - 1, y)) {
                        xSum += (x - 0.5);
                        ySum += y;
                        nF++;
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x, y + 1)) {
                        xSum += x;
                        ySum += (y + 0.5);
                        nF++;
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x, y - 1)) {
                        xSum += x;
                        ySum += (y - 0.5);
                        nF++;
                    }
                    /*if (cc2.containsPixOfCoordinateAbsolute(x + 1, y + 1)) {
                        xSum += (x + 0.5);
                        ySum += (y + 0.5);
                        nF++;
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x - 1, y + 1)) {
                        xSum += (x - 0.5);
                        ySum += (y + 0.5);
                        nF++;
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x + 1, y - 1)) {
                        xSum += (x + 0.5);
                        ySum += (y - 0.5);
                        nF++;
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x - 1, y - 1)) {
                        xSum += (x - 0.5);
                        ySum += (y - 0.5);
                        nF++;
                    }*/
                }
            }
            return new double[]{nF, xSum / nF + 0.5, ySum / nF + 0.5};
        } else {
            int xx = x2;
            int XX = X2;
            int yy = y2;
            int YY = Y2;
            for (int x = xx; x <= XX; x++) {
                for (int y = yy; y <= YY; y++) {

                    if (!cc2.containsPixOfCoordinateAbsolute(x, y)) continue;
                    if (this.containsPixOfCoordinateAbsolute(x + 1, y)) {
                        xSum += (x + 0.5);
                        ySum += y;
                        nF++;
                    }
                    if (this.containsPixOfCoordinateAbsolute(x - 1, y)) {
                        xSum += (x - 0.5);
                        ySum += y;
                        nF++;
                    }
                    if (this.containsPixOfCoordinateAbsolute(x, y + 1)) {
                        xSum += x;
                        ySum += (y + 0.5);
                        nF++;
                    }
                    if (this.containsPixOfCoordinateAbsolute(x, y - 1)) {
                        xSum += x;
                        ySum += (y - 0.5);
                        nF++;
                    }
                    /*if (this.containsPixOfCoordinateAbsolute(x + 1, y + 1)) {
                        xSum += (x + 0.5);
                        ySum += (y + 0.5);
                        nF++;
                    }
                    if (this.containsPixOfCoordinateAbsolute(x - 1, y + 1)) {
                        xSum += (x - 0.5);
                        ySum += (y + 0.5);
                        nF++;
                    }
                    if (this.containsPixOfCoordinateAbsolute(x + 1, y - 1)) {
                        xSum += (x + 0.5);
                        ySum += (y - 0.5);
                        nF++;
                    }
                    if (this.containsPixOfCoordinateAbsolute(x - 1, y - 1)) {
                        xSum += (x - 0.5);
                        ySum += (y - 0.5);
                        nF++;
                    }*/
                }
            }
            return new double[]{nF, xSum / nF + 0.5, ySum / nF + 0.5};
        }
    }

    public double[] nFacets4connexe_V3(CC cc2) {
    if (!isPossibleNeighbour(cc2, false)) return new double[]{0, 0, 0,0,0,0,0};
    double[] firstCalcul = nFacets4connexe_V1(cc2);
    boolean debug=false;
    int x1 = this.xMin;
    int x2 = cc2.xMin;
    int X1 = x1 + this.width;
    int X2 = x2 + cc2.width;
    int y1 = this.yMin;
    int y2 = cc2.yMin;
    int Y1 = y1 + this.height;
    int Y2 = y2 + cc2.height;
    double axisX = 0;
    double axisY = 0;
    double nF = firstCalcul[0];
    double distMin = -1E8;
    double dist = 0;
    double xMinTmp = 0;
    double yMinTmp = 0;
    double xMean=0;
    double yMean=0;
    int countFac=0;
    double xExp = firstCalcul[1];
    double yExp = firstCalcul[2];
    if (width * height < cc2.width * cc2.height) {
            int xx = x1 - 1;
            int XX = X1 + 1;
            int yy = y1 - 1;
            int YY = Y1 + 1;
            for (int x = xx; x <= XX; x++) {
                for (int y = yy; y <= YY; y++) {
                    if (!this.containsPixOfCoordinateAbsolute(x, y)) continue;
                    if (cc2.containsPixOfCoordinateAbsolute(x + 1, y)) {
                        dist = getConnexionScore(cc2, x + 0.5, y, xExp, yExp, debug, 1, 0);
                        xMean+=x+0.5;
                        yMean+=y;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x + 0.5;
                            yMinTmp = y;
                            axisX = 1;
                            axisY = 0;
                        }
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x - 1, y)) {
                        dist = getConnexionScore(cc2, x - 0.5, y, xExp, yExp, debug, -1, 0);
                        xMean+=x-0.5;
                        yMean+=y;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x - 0.5;
                            yMinTmp = y;
                            axisX = -1;
                            axisY = 0;
                        }
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x, y + 1)) {
                        dist = getConnexionScore(cc2, x, y + 0.5, xExp, yExp, debug, 0, 1);
                        xMean+=x;
                        yMean+=y+0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x;
                            yMinTmp = y + 0.5;
                            axisX = 0;
                            axisY = 1;
                        }
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x, y - 1)) {
                        dist = getConnexionScore(cc2, x, y - 0.5, xExp, yExp, debug, 0, -1);
                        xMean+=x;
                        yMean+=y-0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x;
                            yMinTmp = y - 0.5;
                            axisX = 0;
                            axisY = -1;
                        }
                    }
                    /* *
                    if (cc2.containsPixOfCoordinateAbsolute(x + 1, y + 1)) {
                        dist = getConnexionScore(cc2, x + 0.5, y + 0.5, xExp, yExp, debug, 1, 1);
                        xMean+=x+0.5;
                        yMean+=y+0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x + 0.5;
                            yMinTmp = y + 0.5;
                            axisX = 1;
                            axisY = 1;
                        }
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x - 1, y + 1)) {
                        dist = getConnexionScore(cc2, x - 0.5, y + 0.5, xExp, yExp, debug, -1, 1);
                        xMean+=x-0.5;
                        yMean+=y+0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x - 0.5;
                            yMinTmp = y + 0.5;
                            axisX = -1;
                            axisY = 1;
                        }
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x + 1, y - 1)) {
                        dist = getConnexionScore(cc2, x + 0.5, y - 0.5, xExp, yExp, debug, 1, -1);
                        xMean+=x+0.5;
                        yMean+=y-0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x + 0.5;
                            yMinTmp = y - 0.5;
                            axisX = 1;
                            axisY = -1;
                        }
                    }
                    if (cc2.containsPixOfCoordinateAbsolute(x - 1, y - 1)) {
                        dist = getConnexionScore(cc2, x - 0.5, y - 0.5, xExp, yExp, debug, -1, -1);
                        xMean+=x-0.5;
                        yMean+=y-0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x - 0.5;
                            yMinTmp = y - 0.5;
                            axisX = -1;
                            axisY = -1;
                        }
                    }*/
                }
            }
            return new double[]{nF, xMinTmp, yMinTmp, axisX, axisY,xMean/countFac,yMean/countFac};
        } else {
            int xx = x2 - 1;
            int XX = X2 + 1;
            int yy = y2 - 1;
            int YY = Y2 + 1;
            for (int x = xx; x <= XX; x++) {
                for (int y = yy; y <= YY; y++) {

                    if (!cc2.containsPixOfCoordinateAbsolute(x, y)) continue;
                    if (this.containsPixOfCoordinateAbsolute(x + 1, y)) {
                        dist = getConnexionScore(cc2, x + 0.5, y, xExp, yExp, debug, -1, 0);
                        xMean+=x+0.5;
                        yMean+=y;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x + 0.5;
                            yMinTmp = y;
                            axisX = -1;
                            axisY = 0;
                        }
                    }
                    if (this.containsPixOfCoordinateAbsolute(x - 1, y)) {
                        dist = getConnexionScore(cc2, x - 0.5, y, xExp, yExp, debug, 1, 0);
                        xMean+=x-0.5;
                        yMean+=y;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x - 0.5;
                            yMinTmp = y;
                            axisX = 1;
                            axisY = 0;
                        }
                    }
                    if (this.containsPixOfCoordinateAbsolute(x, y + 1)) {
                        dist = getConnexionScore(cc2, x, y + 0.5, xExp, yExp, debug, 0, -1);
                        xMean+=x;
                        yMean+=y+0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x;
                            yMinTmp = y + 0.5;
                            axisX = 0;
                            axisY = -1;
                        }
                    }
                    if (this.containsPixOfCoordinateAbsolute(x, y - 1)) {
                        dist = getConnexionScore(cc2, x, y - 0.5, xExp, yExp, debug, 0, 1);
                        xMean+=x;
                        yMean+=y-0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x;
                            yMinTmp = y - 0.5;
                            axisX = 0;
                            axisY = 1;
                        }
                    }
                    /*if (this.containsPixOfCoordinateAbsolute(x + 1, y + 1)) {
                        dist = getConnexionScore(cc2, x + 0.5, y + 0.5, xExp, yExp, debug, -1, -1);
                        xMean+=x+0.5;
                        yMean+=y+0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x + 0.5;
                            yMinTmp = y + 0.5;
                            axisX = -1;
                            axisY = -1;
                        }
                    }
                    if (this.containsPixOfCoordinateAbsolute(x - 1, y + 1)) {
                        dist = getConnexionScore(cc2, x - 0.5, y + 0.5, xExp, yExp, debug, 1, -1);
                        xMean+=x-0.5;
                        yMean+=y+0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x - 0.5;
                            yMinTmp = y + 0.5;
                            axisX = 1;
                            axisY = -1;
                        }
                    }
                    if (this.containsPixOfCoordinateAbsolute(x + 1, y - 1)) {
                        dist = getConnexionScore(cc2, x + 0.5, y - 0.5, xExp, yExp, debug, -1, 1);
                        xMean+=x+0.5;
                        yMean+=y-0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x + 0.5;
                            yMinTmp = y - 0.5;
                            axisX = -1;
                            axisY = 1;
                        }
                    }
                    if (this.containsPixOfCoordinateAbsolute(x - 1, y - 1)) {
                        dist = getConnexionScore(cc2, x - 0.5, y - 0.5, xExp, yExp, debug, 1, 1);
                        xMean+=x-0.5;
                        yMean+=y-0.5;
                        countFac++;
                        if (dist > distMin) {
                            distMin = dist;
                            xMinTmp = x - 0.5;
                            yMinTmp = y - 0.5;
                            axisX = 1;
                            axisY = 1;
                        }
                    }*/
                }
            }
            return new double[]{nF, xMinTmp, yMinTmp, axisX, axisY,xMean/countFac,yMean/countFac};
        }
    }

    public SimpleWeightedGraph<Pix, Bord> buildConnectionGraphOfComponent(ImagePlus imgSeg, ImagePlus distToExt,
																		  int connexity) {
        this.pixGraph = new SimpleWeightedGraph<>(Bord.class);
        Pix[][] tabPix = new Pix[imgSeg.getWidth()][imgSeg.getHeight()];
        int xM = imgSeg.getWidth();
        int yM = imgSeg.getHeight();
        byte[] tabSeg = (byte[]) imgSeg.getStack().getPixels(1);
        float[] tabDist = (float[]) distToExt.getStack().getPixels(1);
        for (int x = 0; x < xM; x++)
            for (int y = 0; y < yM; y++)
                if (Utils.toInt(tabSeg[y * xM + x]) > 0) {
                    tabPix[x][y] = new Pix(x, y, tabDist[y * xM + x]);
                    pixGraph.addVertex(tabPix[x][y]);
                }
        for (int x = 0; x < xM; x++)
            for (int y = 0; y < yM; y++) {
                if (tabPix[x][y] == null) continue;
                if ((x < (xM - 1)) && (y < (yM - 1)) && (connexity == 8) &&(tabPix[x + 1][y + 1] != null)){
                    this.pixGraph.addEdge(tabPix[x][y], tabPix[x + 1][y + 1], new Bord(tabPix[x][y],tabPix[x + 1][y + 1]));
                }
                if ((x < (xM - 1)) && (y > 0) && (connexity == 8) && (tabPix[x + 1][y - 1] != null)){
                    this.pixGraph.addEdge(tabPix[x][y], tabPix[x + 1][y - 1], new Bord(tabPix[x][y],tabPix[x + 1][y - 1]));
                }
                if ((x < (xM - 1))) if (tabPix[x + 1][y] != null){
                    this.pixGraph.addEdge(tabPix[x][y], tabPix[x + 1][y], new Bord(tabPix[x][y], tabPix[x + 1][y]));
                }
                if ((y < (yM - 1))) if (tabPix[x][y + 1] != null){
                    this.pixGraph.addEdge(tabPix[x][y], tabPix[x][y + 1], new Bord(tabPix[x][y], tabPix[x][y + 1]));
                }
            }
        return this.pixGraph;
    }


    public double getConnexionScore(CC cc, double x, double y, double expectedX, double expectedY, boolean debug,
									double vx, double vy) {
        double[] vectFace = new double[]{vx, vy, 0};
        double[] vectDays = new double[]{cc.x() - this.x(), cc.y() - this.y(), 0};
        double score0 =
				TransformUtils.scalarProduct(vectFace, vectDays) / (TransformUtils.norm(vectFace) * TransformUtils.norm(vectDays));
        double score1 = VitimageUtils.distance(this.x(), this.y(), x, y) - VitimageUtils.distance(cc.x(), cc.y(), x,
				y);//Foster being nearer to destination
        double cost1 = VitimageUtils.distance(expectedX, expectedY, x, y);//Foster being near the expected point
        double score2 = 1E8;
        for (double dx = -10; dx <= 10; dx += 0.5)
            for (double dy = -10; dy <= 10; dy += 0.5) {
                if ((!cc.containsPixOfCoordinateAbsolute((int) Math.round(x + dx), (int) Math.round(y + dy))) && (!this.containsPixOfCoordinateAbsolute((int) Math.round(x + dx), (int) Math.round(y + dy))) && (score2 > Math.sqrt(dx * dx + dy * dy))) {
                    score2 = Math.sqrt(dx * dx + dy * dy);
                }
            }
        if (debug)
            System.out.println(("X=" + x + " Y=" + y + " Total=" + (2 * score0 + 2 * score2) + " " +
					"score0=" + score0 + "  score1=" + score1 + " cost1=" + cost1 + " score2=" + score2 + " with exp=" + expectedX + "," + expectedY));
        return (2 * score0 +2 * score2);
    }









/******************************************************************************************************************************************************************************************************
 * Rendering methods
 */
    public ImagePlus drawDist() {
        ImagePlus seg = VitimageUtils.convertToFloat(this.thisSeg);
        ImageProcessor ip = seg.getStack().getProcessor(1);
        for (Pix p : pixGraph.vertexSet()) ip.setf(p.x, p.y, (float) p.dist);
        seg.setProcessor(ip);
        seg.resetDisplayRange();
        seg.setTitle("Dist");
        IJ.run(seg, "Fire", "");
        seg.setDisplayRange(0, 100);
        return seg;
    }

    public ImagePlus drawDistToSkeleton() {
        ImagePlus seg = VitimageUtils.convertToFloat(this.thisSeg);
        ImageProcessor ip = seg.getStack().getProcessor(1);
        for (Pix p : pixGraph.vertexSet()) ip.setf(p.x, p.y, (float) p.distanceToSkeleton);
        seg.setProcessor(ip);
        seg.setTitle("DistToSkeleton");
        IJ.run(seg, "Fire", "");
        seg.setDisplayRange(0, 30);
        return seg;
    }

    public ImagePlus drawWayFromPrim() {
        ImagePlus seg = VitimageUtils.convertToFloat(this.thisSeg);
        ImageProcessor ip = seg.getStack().getProcessor(1);
        for (Pix p : pixGraph.vertexSet()) ip.setf(p.x, p.y, (float) p.wayFromPrim);
        seg.setProcessor(ip);
        seg.setTitle("WayFromPrim");
        IJ.run(seg, "Fire", "");
        seg.setDisplayRange(0, 500);
        return seg;
    }








/******************************************************************************************************************************************************************************************************
 * Methods at the level of the corresponding root in architecture 
 */
    public CC getChild(){
        CC ccBest=null;
        for(CC cc : graph.outgoingEdgesOf(this).stream().filter(e->e.activated).map(e->e.target).collect(Collectors.toList())){
            if(cc.order==this.order){ccBest=cc;break;}
        }
        return ccBest;
    }

    public CC getParent(){
        CC ccBest=null;
        for(CC cc : graph.incomingEdgesOf(this).stream().filter(e->e.activated).map(e->e.source).collect(Collectors.toList())){
            if(cc.order==this.order){ccBest=cc;break;}
        }
        return ccBest;
    }

    public boolean hasHiddenChild(){
        CC child=this.getChild();
        ConnectionEdge edge=graph.getEdge(this, child);
        if(child==null) return false;
        return edge.hidden;
    }


    public boolean hasHiddenParent(){
        CC parent=this.getParent();
        ConnectionEdge edge=graph.getEdge(parent,this);
        if(parent==null) return false;
        return edge.hidden;
    }


    
   /*   public CC getLatChild() {
        if (this.bestOutgoingActivatedCC() == null) return null;
        return this.bestOutgoingActivatedCC();
    }

    public boolean isHiddenLatChild() {
        if (this.bestOutgoingActivatedCC() == null) return false;
        return this.bestOutgoingActivatedEdge().hidden;
    }


    public CC getPrimChild() {
        if (!trunk) return null;
        if (this.bestOutgoingActivatedCC() == null) return null;
        for (ConnectionEdge edge : graph.outgoingEdgesOf(this)) {
            if (edge.target.trunk) return edge.target;
        }
        return null;
    }

    public boolean isHiddenPrimChild() {
        if (!trunk) return false;
        if (this.bestOutgoingActivatedCC() == null) return false;
        for (ConnectionEdge edge : graph.outgoingEdgesOf(this)) {
            if (edge.target.trunk) return edge.hidden;
        }
        return false;
    }*/

    public void updateAllDistancesToTrunk() {
        ArrayList<Pix> tabVisited = new ArrayList<Pix>();
        ArrayList<Pix> tabToVisit = new ArrayList<Pix>();
        for (Pix p : pixGraph.vertexSet()) {
            if (mainDjikstraPath.contains(p)) {
                tabVisited.add(p);
                p.distanceToSkeleton = 0;
                p.previous = p;
            } else {
                p.distanceToSkeleton = 10000000;
            }
        }
        while (tabVisited.size() > 0) {
            for (Pix p : tabVisited) {
                for (Bord bord : pixGraph.edgesOf(p)) {
                    Pix p1 = pixGraph.getEdgeSource(bord);
                    if (p1 != p) if (p1.distanceToSkeleton > (p.distanceToSkeleton + pixGraph.getEdge(p1, p).len)) {
                        p1.previous = p.previous;
                        p1.distanceToSkeleton = p.distanceToSkeleton + pixGraph.getEdge(p1, p).len;
                        tabToVisit.add(p1);
                    }
                    Pix p2 = pixGraph.getEdgeTarget(bord);
                    if (p2 != p) if (p2.distanceToSkeleton > p.distanceToSkeleton + pixGraph.getEdge(p2, p).len) {
                        p2.previous = p.previous;
                        p2.distanceToSkeleton = p.distanceToSkeleton + pixGraph.getEdge(p2, p).len;
                        tabToVisit.add(p2);
                    }
                }
            }
            tabVisited = tabToVisit;
            tabToVisit = new ArrayList<Pix>();
        }
        for (Pix p : pixGraph.vertexSet()) if (!mainDjikstraPath.contains(p)) p.wayFromPrim = p.previous.wayFromPrim;
    }

    public CC getActivatedLeafOfCC(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, CC cc) {
        CC ccNext = cc;
        while (ccNext.bestOutgoingActivatedEdge() != null) ccNext = bestOutgoingActivatedEdge().target;
        return ccNext;
    }

    public CC getActivatedRoot() {
        CC ccPrev = this;
        while ((ccPrev.bestIncomingActivatedEdge() != null) && ((!ccPrev.bestIncomingActivatedEdge().source.trunk)) && (!(ccPrev.bestIncomingActivatedEdge().source.day < 2))) {
            ccPrev = ccPrev.bestIncomingActivatedEdge().source;
            if (ccPrev == null) return null;
            if (ccPrev == this) return null;
        }
        return ccPrev;
    }

    public ConnectionEdge bestCountOutgoingEdge() {
        double maxPix = -1;
        double minCost = 1E18;
        ConnectionEdge bestEdge = null;
        if (graph.outgoingEdgesOf(this).size() > 0) {
            for (ConnectionEdge edge : graph.outgoingEdgesOf(this)) {
                if ((graph.getEdgeTarget(edge).count == maxPix && graph.getEdgeWeight(edge) < minCost) ||
                        (graph.getEdgeTarget(edge).count > maxPix)) {
                    minCost = graph.getEdgeWeight(edge);
                    maxPix = graph.getEdgeTarget(edge).count;
                    bestEdge = edge;
                }
            }
        }
        return bestEdge;
    }
    
    public ConnectionEdge bestCountOutgoingEdge_v2() {
        double maxVal = -100000000;
        ConnectionEdge bestEdge = null;
        if (graph.outgoingEdgesOf(this).size() > 0) {
            for (ConnectionEdge edge : graph.outgoingEdgesOf(this)) {
                double val =
						graph.getEdgeTarget(edge).count * 1.0 / (VitimageUtils.EPSILON + 1 + graph.getEdgeWeight(edge));
                if (val < 0) {
                    System.out.println("En prison ! " + val);
                }
                if (val > maxVal) {
                    maxVal = val;
                    bestEdge = edge;
                }
            }
        }
        return bestEdge;
    }

    public void lightOffLateralRoot() {
        if (!isLatStart) return;
        CC ccOld = this;
        CC ccTmp = this;
        ConnectionEdge edge = null;
        while (ccTmp.bestOutgoingActivatedCC() != null) {
            ccOld = ccTmp;
            ccTmp = ccOld.bestOutgoingActivatedCC();
            edge = ccTmp.bestIncomingActivatedEdge();
            graph.removeVertex(ccOld);
            graph.removeEdge(edge);
        }
        graph.removeVertex(ccTmp);
    }

    public void setOut() {
        this.isOut = true;
        for (ConnectionEdge edge : graph.outgoingEdgesOf(this)) edge.isOut = true;
        for (ConnectionEdge edge : graph.incomingEdgesOf(this)) edge.isOut = true;
    }

    








/******************************************************************************************************************************************************************************************************
 * Methods at the level of the graph and neighbours 
 */

    public CC bestOutgoingActivatedCC() {
        ConnectionEdge edge = bestOutgoingActivatedEdge();
        if (edge != null) return edge.target;
        return null;
    }

    public CC bestIncomingActivatedCC() {
        ConnectionEdge edge = bestIncomingActivatedEdge();
        if (edge != null) return edge.source;
        return null;
    }

    
    public ConnectionEdge bestIncomingEdge() {
        double minCost = 1E18;
        ConnectionEdge bestEdge = null;
        if (graph.incomingEdgesOf(this).size() > 0) {
            for (ConnectionEdge edge : graph.incomingEdgesOf(this)) {
                if (edge.cost < minCost) {
                    minCost = edge.cost;
                    bestEdge = edge;
                }
            }
        }
        return bestEdge;
    }

    public ConnectionEdge bestOutgoingActivatedEdge() {
        double minCost = 1E18;
        ConnectionEdge bestEdge = null;
        if (graph.outgoingEdgesOf(this).size() > 0) {
            for (ConnectionEdge edge : graph.outgoingEdgesOf(this)) {
                if (edge.activated && edge.cost < minCost) {
                    minCost = edge.cost;
                    bestEdge = edge;
                }
            }
        }
        return bestEdge;
    }

    public ConnectionEdge bestIncomingActivatedEdge() {
        double minCost = 1E18;
        ConnectionEdge bestEdge = null;
        if (graph.incomingEdgesOf(this).size() > 0) {
            for (ConnectionEdge edge : graph.incomingEdgesOf(this)) {
                if (edge.activated && edge.cost < minCost) {
                    minCost = edge.cost;
                    bestEdge = edge;
                }
            }
        }
        return bestEdge;
    }

    public ConnectionEdge bestOutgoingEdge() {
        double minCost = 1E18;
        ConnectionEdge bestEdge = null;
        if (graph.outgoingEdgesOf(this).size() > 0) {
            for (ConnectionEdge edge : graph.outgoingEdgesOf(this)) {
                if (edge.cost < minCost) {
                    minCost = edge.cost;
                    bestEdge = edge;
                }
            }
        }
        return bestEdge;
    }

    public ConnectionEdge incomingEdgeInOrgan() {
        if (graph.incomingEdgesOf(this).size() > 0) {
            for (ConnectionEdge edge : graph.incomingEdgesOf(this)) {
                if(edge.source.order==this.order && edge.activated){
                    return edge;
                }
            }
        }
        return null;
    }


    public CC incomingCCInOrgan() {
        ConnectionEdge edge = incomingEdgeInOrgan();
        if(edge!=null) return edge.source;
        return null;
    }

    public ConnectionEdge followingEdgeInOrgan() {
        double minCost = 1E18;
        ConnectionEdge bestEdge = null;
        if (graph.outgoingEdgesOf(this).size() > 0) {
            for (ConnectionEdge edge : graph.outgoingEdgesOf(this)) {
                if(edge.target.order==this.order && edge.activated){
                    return edge;
                }
            }
        }
        return null;
    }

    public CC followingCCInOrgan() {
        ConnectionEdge edge = followingEdgeInOrgan();
        if(edge!=null) return edge.target;
        return null;
    }


    public boolean isPossibleNeighbour(CC cc2, boolean debug) {
        int x1 = this.xMin;
        int x2 = cc2.xMin;
        int X1 = x1 + this.width;
        int X2 = x2 + cc2.width;
        int y1 = this.yMin;
        int y2 = cc2.yMin;
        int Y1 = y1 + this.height;
        int Y2 = y2 + cc2.height;
        if (x1 >= X2 + 1) return false;
        if (x2 >= X1 + 1) return false;
        if (y1 >= Y2 + 1) return false;
        if (y2 >= Y1 + 1) return false;
        return true;
    }










/******************************************************************************************************************************************************************************************************
 * Methods for dikjstra, weights, distances
 */

    public void setWeightsToDistExt() {
        for (Bord bord : pixGraph.edgeSet()) pixGraph.setEdgeWeight(bord, bord.getWeightDistExt());
    }

    public void setWeightsToEuclidian() {
        for (Bord bord : pixGraph.edgeSet()) pixGraph.setEdgeWeight(bord, bord.getWeightEuclidian());
    }

    public double setDistanceToShortestPath(double distanceAtStart) {
        double curDist = distanceAtStart;
        this.mainDjikstraPath.get(0).wayFromPrim = curDist;
        for (int i = 1; i < this.mainDjikstraPath.size(); i++) {
            Pix p = this.mainDjikstraPath.get(i);
            Pix pBef = this.mainDjikstraPath.get(i - 1);
            curDist += this.pixGraph.getEdge(p, pBef).len;
            p.wayFromPrim = curDist;
        }
        return curDist;
    }

    public int[] getSeedFromFacetConnexion(double[] coords, double[] vectPrev, boolean justDebug) {
        double x0 = coords[0];
        double y0 = coords[1];
        System.out.println("In GetSeed [],bool Recherche de " + x0 + "," + y0);
        if (x0 == (int) Math.round(x0)) {
            System.out.println("Cas 1-X");
            if (y0 == (int) Math.round(y0)) {
                System.out.println("Cas 1-1");
                System.out.println("Pb 1 dans CC");
            } else {//Facette suivant y
                if (containsPixOfCoordinateAbsolute((int) x0, (int) Math.round(y0 - 0.5))) y0 -= 0.5;
                else if (containsPixOfCoordinateAbsolute((int) x0, (int) Math.round(y0 + 0.5))) y0 += 0.5;
                else System.out.println("Pb 2 dans CC");
            }
        } else {//
            System.out.println("Cas 2-X");
            if (y0 == (int) Math.round(y0)) {
                if (containsPixOfCoordinateAbsolute((int) Math.round(x0 - 0.5), (int) y0)) x0 -= 0.5;
                else if (containsPixOfCoordinateAbsolute((int) Math.round(x0 + 0.5), (int) y0)) x0 += 0.5;
                else System.out.println("Pb 3 dans CC");
            } else {
                System.out.println("Cas 2-2");
                System.out.println("Running x0y0=" + x0 + "," + y0);
                if (containsPixOfCoordinateAbsolute((int) Math.round(x0 - 0.5), (int) Math.round(y0 - 0.5))) {
                    System.out.println("v1");
                    x0 -= 0.5;
                    y0 -= 0.5;
                } else if (containsPixOfCoordinateAbsolute((int) Math.round(x0 - 0.5), (int) Math.round(y0 + 0.5))) {
                    System.out.println("v2");
                    x0 -= 0.5;
                    y0 += 0.5;
                } else if (containsPixOfCoordinateAbsolute((int) Math.round(x0 + 0.5), (int) Math.round(y0 - 0.5))) {
                    System.out.println("v3");
                    x0 += 0.5;
                    y0 -= 0.5;
                } else if (containsPixOfCoordinateAbsolute((int) Math.round(x0 + 0.5), (int) Math.round(y0 + 0.5))) {
                    System.out.println("v4");
                    x0 += 0.5;
                    y0 += 0.5;
                } else System.out.println("Pb 4 dans CC");
            }
        }
        System.out.println("Got x0y0=" + x0 + "," + y0);
    System.out.println("Give=" + (int) Math.round(x0 - this.xMin()) + "," + (int) Math.round(y0 - this.yMin()));
    return new int[]{(int) Math.round(x0 - this.xMin()), (int) Math.round(y0 - this.yMin())};
    }

    public boolean containsPixOfCoordinateAbsolute(int xAbs, int yAbs){
        int xRel=xAbs-this.xMin;
        int yRel=yAbs-this.yMin;
        return containsPixOfCoordinateRelative(xRel,yRel);
    }

    public boolean containsPixOfCoordinateRelative(int xRel, int yRel){
        if(xRel<0 || xRel>=this.width || yRel<0 || yRel>=this.height) return false;
        byte[] tabDataMask = (byte[])this.thisSeg.getStack().getPixels(1);
        if(Utils.toInt(tabDataMask[yRel*this.width+xRel])==0) return false;
        return true;
    }
    
    public int[] getPrevTargetFromFacetConnexion(ConnectionEdge e) {
        double x0 = e.connectionX - e.source.xMin();
        double y0 = e.connectionY - e.source.yMin();
        double dx = e.axisX / 2.0;
        double dy = e.axisY / 2.0;
        return new int[]{(int) Math.round(x0 - dx), (int) Math.round(y0 - dy)};
    }
    
    public int[] getNextSourceFromFacetConnexion(ConnectionEdge e) {
        double x0 = e.connectionX - e.target.xMin();
        double y0 = e.connectionY - e.target.yMin();
        double dx = e.axisX / 2.0;
        double dy = e.axisY / 2.0;
        if (getPix((int) Math.round(x0 + dx), (int) Math.round(y0 + dy)) == null) {
            int targX = (int) Math.round(x0 + dx);
            int targY = (int) Math.round(y0 + dy);
            double minDist = 1000000;
            Pix pp = null;
            for (Pix p : pixGraph.vertexSet()) {
                double dist = VitimageUtils.distance(targX, targY, p.x, p.y);
                if (dist < minDist) {
                    minDist = dist;
                    pp = p;
                }
            }
            return new int[]{pp.x, pp.y};
        }
        else return new int[]{(int) Math.round(x0 + dx), (int) Math.round(y0 + dy)};
    }

    public Pix getNearestPix(double xRel, double yRel) {
        //Get the nearest pixel
        double distMin=1000000;
        Pix pixMin=null;
        for(Pix p : pixGraph.vertexSet()){
            double dist=VitimageUtils.distance(xRel-xMin,yRel-yMin,p.x,p.y);
            if(dist<distMin){
                distMin=dist;
                pixMin=p;
            }
        }
        return pixMin;
    }


    public int[] determineTargetGeodesicallyFarestFromTheSource(int[] start) {
        int x0 = start[0];
        int y0 = start[1];
        //ImagePlus imgSeg = null;
        //if (thisSeg == null) imgSeg = VitimageUtils.projectRoiOnSubImage(this.r);
        //else imgSeg = thisSeg;
        ImagePlus seedImage = VitimageUtils.convertToFloat(VitimageUtils.nullImage(this.thisSeg));
        ((float[]) (seedImage.getStack().getPixels(1)))[width * y0 + x0] = 255;
        ImagePlus distance = MorphoUtils.computeGeodesic(seedImage, this.thisSeg, false);
        float[] tabData = (float[]) distance.getStack().getPixels(1);
        int xMax = 0;
        int yMax = 0;
        double distMax = -1000;
        double eucDistMax = -1000;
        for (int x = 0; x < distance.getWidth(); x++)
            for (int y = 0; y < distance.getHeight(); y++) {
                if (tabData[distance.getWidth() * y + x] == distMax) {
                    if (VitimageUtils.distance(x, y, x0, y0) > eucDistMax) {
                        eucDistMax = VitimageUtils.distance(x, y, x0, y0);
                        xMax = x;
                        yMax = y;
                    }
                } else if (tabData[distance.getWidth() * y + x] > distMax) {
                    distMax = tabData[distance.getWidth() * y + x];
                    eucDistMax = VitimageUtils.distance(x, y, x0, y0);
                    xMax = x;
                    yMax = y;
                }
            }
        return new int[]{xMax, yMax};
    }

    public int[][] findHiddenStartStopToInOtherCC(CC cc2, int[] start) {
        int[][] res = new int[4][2];
        int x0 = start[0] + this.xMin - cc2.xMin;
        int y0 = start[1] + this.yMin - cc2.yMin;
        double distMin = 1E8;
        int xMin = 0;
        int yMin = 0;
        for (Pix p : cc2.pixGraph.vertexSet()) {
            if (VitimageUtils.distance(x0, y0, p.x, p.y) < distMin) {
                distMin = VitimageUtils.distance(x0, y0, p.x, p.y);
                xMin = p.x;
                yMin = p.y;
            }
        }
        res[1] = new int[]{xMin, yMin};
        res[3] = new int[]{xMin + cc2.xMin, yMin + cc2.yMin};
        distMin = 1E8;
        xMin = 0;
        yMin = 0;
        x0 = xMin + cc2.xMin - this.xMin;
        y0 = yMin + cc2.yMin - this.yMin;
        for (Pix p : this.pixGraph.vertexSet()) {
            if (VitimageUtils.distance(x0, y0, p.x, p.y) < distMin) {
                distMin = VitimageUtils.distance(x0, y0, p.x, p.y);
                xMin = p.x;
                yMin = p.y;
            }
        }
        res[0] = new int[]{xMin, yMin};
        res[2] = new int[]{xMin + this.xMin, yMin + this.yMin};
        return res;
    }

    public String getStringDijkstraMainPath() {
        String ret = "";
        for (Pix p : mainDjikstraPath) ret += (p + "\n");
        return ret;
    }

    public int[] getExpectedSource() {
        if (this.isPrimStart) {
            Pix pp = null;
            int yMin = 100000;
            for (Pix p : pixGraph.vertexSet()) {
                if (p.y < yMin) {
                    pp = p;
                    yMin = p.y;
                }
            }
            return new int[]{pp.x, pp.y};
        }
        ConnectionEdge edge = bestIncomingActivatedEdge();
        return getNextSourceFromFacetConnexion(edge);
    }

    public int[] getExpectedTarget() {
        ConnectionEdge edge = bestOutgoingActivatedEdge();
        return getPrevTargetFromFacetConnexion(edge);
    }

    public void determineVoxelShortestPathTrunkRoot() {
        int[] coordsT = new int[]{10000000, -1};
        int[] coordsS = new int[]{1000000, 1000000};
        if (!(getChild() == null)) {
            CC nextPrim = getChild();
            ConnectionEdge edge = graph.getEdge(this, nextPrim);
            coordsT = getPrevTargetFromFacetConnexion(edge);
        } else {
            for (Pix p : this.pixGraph.vertexSet()) {
                if (p.y > coordsT[1]) {
                    coordsT[0] = p.x;
                    coordsT[1] = p.y;
                }
            }
        }
        if (this.day > 1) {
            CC prevPrim = bestIncomingActivatedCC();
            ConnectionEdge edge = graph.getEdge(prevPrim, this);
            coordsS = getNextSourceFromFacetConnexion(edge);
        } else {
            for (Pix p : this.pixGraph.vertexSet())
                if (p.y < coordsS[1]) {
                    coordsS[0] = p.x;
                    coordsS[1] = p.y;
                }
        }

        determineVoxelShortestPath(coordsS, coordsT, 8, null);
        if (!RegionAdjacencyGraphPipelineV2.isExtremity(this, graph)) {
            for (ConnectionEdge edges : graph.outgoingEdgesOf(this)) {
                if (edges.target.trunk) continue;
                if (!edges.activated) continue;
                coordsT = getPrevTargetFromFacetConnexion(edges);
                determineVoxelShortestPath(coordsS, coordsT, 8, edges.target);
            }
        }
    }

    public List<Pix> determineVoxelShortestPath(int[] coordStart, int[] coordStop, int connexity,
                                                CC setHereNextCCIfItIsLatDeterminationForTrunk) {
        Pix pixStart = this.getPix(coordStart[0], coordStart[1]);
        Pix pixStop = this.getPix(coordStop[0], coordStop[1]);
        setWeightsToDistExt();
        DijkstraShortestPath<Pix, Bord> djik = new DijkstraShortestPath<Pix, Bord>(this.pixGraph);
        GraphPath<Pix, Bord> path = djik.getPath(pixStart, pixStop);
        
        if (setHereNextCCIfItIsLatDeterminationForTrunk == null) {
            this.mainDjikstraPath = path.getVertexList();
            for (Pix p : this.mainDjikstraPath) p.isSkeleton = true;
        } else {
            List<Pix> temp = path.getVertexList();
            List<Pix> definitive = path.getVertexList();
            definitive.clear();
            for (Pix p : temp) {
                if (!this.mainDjikstraPath.contains(p)) definitive.add(p);
            }
            this.secondaryDjikstraPath.add(definitive);
            for (Pix p : definitive) p.isSkeleton = true;
            this.secondaryPathLookup.add(setHereNextCCIfItIsLatDeterminationForTrunk);
        }
        return path.getVertexList();
    }

    public static double euclidianlenghtOfPixPath(List<Pix> path) {
        if (path == null) return 0;
        if (path.size() < 2) return 0;
        double len = 0;
        for (int i = 1; i < path.size(); i++) {
            Pix p = path.get(i);
            Pix pBef = path.get(i - 1);
            double dx=p.x-pBef.x;
            double dy=p.y-pBef.y;
            double dist=Math.sqrt(dx*dx+dy*dy);
            len += dist;
        }
        return len;
    }

    public double setDistancesToShortestPathTrunk() {
        double curDist = 0;
        if (this.day > 1 && bestIncomingActivatedEdge() != null) {
            Pix p = this.mainDjikstraPath.get(0);
            double xEd = bestIncomingActivatedEdge().connectionX - this.xMin();
            double yEd = bestIncomingActivatedEdge().connectionY - this.yMin();
            double delta = VitimageUtils.distance(xEd, yEd, p.x, p.y);
            curDist = bestIncomingActivatedEdge().distanceConnectionTrunk + delta;
        }
        this.mainDjikstraPath.get(0).wayFromPrim = curDist;
        for (int i = 1; i < this.mainDjikstraPath.size(); i++) {
            Pix p = this.mainDjikstraPath.get(i);
            Pix pBef = this.mainDjikstraPath.get(i - 1);
            curDist += this.pixGraph.getEdge(p, pBef).len;
            p.wayFromPrim = curDist;
        }
        CC nextPrim = getChild();
        if (nextPrim != null) {
            Pix p = this.mainDjikstraPath.get(this.mainDjikstraPath.size() - 1);
            ConnectionEdge edge = graph.getEdge(this, nextPrim);
            double delta = VitimageUtils.distance(edge.connectionX - this.xMin(), edge.connectionY - this.yMin(), p.x, p.y);
            edge.distanceConnectionTrunk = curDist + delta;
        }

        if (this.secondaryDjikstraPath == null) return curDist;
        double curDist2 = 0;
        for (int j = 0; j < this.secondaryDjikstraPath.size(); j++) {
            curDist2 = 0;
            List<Pix> sec = secondaryDjikstraPath.get(j);
            for (int i = 1; i < sec.size(); i++) {
                Pix p = sec.get(i);
                Pix pBef = sec.get(i - 1);
                curDist2 += this.pixGraph.getEdge(p, pBef).len;
                p.wayFromPrim = curDist2;
            }
            CC nextLat = this.secondaryPathLookup.get(j);
            ConnectionEdge edge2 = graph.getEdge(this, nextLat);
            edge2.distanceConnectionTrunk = curDist2;

        }
        return curDist;
    }

    public double setDistancesToMainDijkstraPath(double d0) {
        double tot = d0;
        this.mainDjikstraPath.get(0).wayFromPrim = tot;
        for (int i = 1; i < this.mainDjikstraPath.size(); i++) {
            Pix p = this.mainDjikstraPath.get(i);
            Pix pBef = this.mainDjikstraPath.get(i - 1);
            tot += this.pixGraph.getEdge(p, pBef).len;
            p.wayFromPrim = tot;
        }
        return tot;
    }





/** --------------------------------------------------------------------------------------------------------------------------------------------
 * 
 * 
 * 
 * 
 * A revoir un jour
       
 
     public static void CCConstructor old(int day, double[]hours, int n, Roi r, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        this.secondaryDjikstraPath = new ArrayList<List<Pix>>();
        this.secondaryPathLookup = new ArrayList<CC>();
        this.day = day;
        this.hour = hours[0];
        this.hourGuessedOfStart=hours[1];
        this.hourGuessedOfCentroid=hours[2];
        this.hourGuessedOfTip=hours[3];
        this.n = n;
        this.setRoi(r);
        this.x = r.getContourCentroid()[0];
        this.y = r.getContourCentroid()[1];
    this.xMin = this.r.getBounds().x;
    this.yMin = this.r.getBounds().y;
        this.graph = graph;
        ImagePlus imgSeg = VitimageUtils.projectRoiOnSubImage(this.r);
        ImagePlus dist = MorphoUtils.computeGeodesicInsideComponent(imgSeg, 0.1);
        double val = VitimageUtils.maxOfImage(dist);
        dist = VitimageUtils.makeOperationOnOneImage(dist, 2, ratioFuiteBordSurLongueur / val, true);
        dist = VitimageUtils.makeOperationOnOneImage(dist, 1, 1, true);
        buildConnectionGraphOfComponent(imgSeg, dist, 8);
        identifySafeCenter();
    }



    public CC(CC source) {
        this.nPixels = source.nPixels;
        this.day = source.day;
        this.hour = source.hour;
        this.hourGuessedOfStart=source.hourGuessedOfStart;
        this.hourGuessedOfCentroid=source.hourGuessedOfCentroid;
        this.hourGuessedOfTip=source.hourGuessedOfTip;
        this.n = source.n;
        this.stamp = source.stamp;
        this.stamp2 = source.stamp2;
        this.componentLabel = source.componentLabel;
        this.r = (Roi) source.r.clone();
        //TODO : should I copy all the other fields ?   
    }
 
 
 



 
 
 
 public double[] nFacets4connexe_V22(CC cc2) {
        if (!isPossibleNeighbour(cc2, false)) return new double[]{0, 0, 0};
        double[] firstCalcul = nFacets4connexe_V1(cc2);
        Rectangle R1 = this.r.getBounds();
        Rectangle R2 = cc2.r.getBounds();
        Roi r1 = this.r;
        Roi r2 = cc2.r;
        int x1 = R1.x;
        int x2 = R2.x;
        int X1 = x1 + R1.width;
        int X2 = x2 + R2.width;
        int y1 = R1.y;
        int y2 = R2.y;
        int Y1 = y1 + R1.height;
        int Y2 = y2 + R2.height;
        double nF = firstCalcul[0];
        double distMin = 1E8;
        double dist = 0;
        double xMin = 0;
        double yMin = 0;
        if (R1.width * R1.height < R2.width * R2.height) {
            int xx = x1;
            int XX = X1;
            int yy = y1;
            int YY = Y1;
            for (int x = xx; x <= XX; x++) {
                for (int y = yy; y <= YY; y++) {
                    if (!r1.contains(x, y)) continue;
                    if (r2.contains(x + 1, y)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x + 0.5, y);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x + 0.5;
                            yMin = y;
                        }
                    }
                    if (r2.contains(x - 1, y)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x - 0.5, y);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x - 0.5;
                            yMin = y;
                        }
                    }
                    if (r2.contains(x, y + 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x, y + 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x;
                            yMin = y + 0.5;
                        }
                    }
                    if (r2.contains(x, y - 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x, y - 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x;
                            yMin = y - 0.5;
                        }
                    }
                    if (r2.contains(x + 1, y + 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x + 0.5, y + 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x + 0.5;
                            yMin = y + 0.5;
                        }
                    }
                    if (r2.contains(x - 1, y + 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x - 0.5, y + 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x - 0.5;
                            yMin = y + 0.5;
                        }
                    }
                    if (r2.contains(x + 1, y - 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x + 0.5, y - 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x + 0.5;
                            yMin = y - 0.5;
                        }
                    }
                    if (r2.contains(x - 1, y - 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x - 0.5, y - 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x - 0.5;
                            yMin = y - 0.5;
                        }
                    }
                }
            }
            return new double[]{nF, xMin, yMin};
        } else {
            int xx = x2;
            int XX = X2;
            int yy = y2;
            int YY = Y2;
            for (int x = xx; x <= XX; x++) {
                for (int y = yy; y <= YY; y++) {

                    if (!r2.contains(x, y)) continue;
                    if (r1.contains(x + 1, y)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x + 0.5, y);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x + 0.5;
                            yMin = y;
                        }
                    }
                    if (r1.contains(x - 1, y)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x - 0.5, y);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x - 0.5;
                            yMin = y;
                        }
                    }
                    if (r1.contains(x, y + 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x, y + 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x;
                            yMin = y + 0.5;
                        }
                    }
                    if (r1.contains(x, y - 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x, y - 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x;
                            yMin = y - 0.5;
                        }
                    }
                    if (r1.contains(x + 1, y + 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x + 0.5, y + 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x + 0.5;
                            yMin = y + 0.5;
                        }
                    }
                    if (r1.contains(x - 1, y + 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x - 0.5, y + 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x - 0.5;
                            yMin = y + 0.5;
                        }
                    }
                    if (r1.contains(x + 1, y - 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x + 0.5, y - 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x + 0.5;
                            yMin = y - 0.5;
                        }
                    }
                    if (r1.contains(x - 1, y - 1)) {
                        dist = VitimageUtils.distance(firstCalcul[1], firstCalcul[2], x - 0.5, y - 0.5);
                        if (dist < distMin) {
                            distMin = dist;
                            xMin = x - 0.5;
                            yMin = y - 0.5;
                        }
                    }
                }
            }
            return new double[]{nF, xMin, yMin};
        }
    }

 
 public int[] getSeedFromFacetConnexionOLD(double[] coords, boolean justDebug) {
    double x0 = coords[0] + this.xMin();
        double y0 = coords[1] + this.yB();
        int[] coInt = new int[]{(int) Math.round(x0), (int) Math.round(y0)};
        double min = 10;
        int xMin = coInt[0];
        int yMin = coInt[1];
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = -3; dy <= 3; dy++) {
                int xf = coInt[0] + dx;
                int yf = coInt[1] + dy;
                if (this.r.contains(xf, yf) && VitimageUtils.distance(x0, y0, xf, yf) < min) {
                    min = VitimageUtils.distance(x0, y0, xf, yf);
                    xMin = xf;
                    yMin = yf;
                }
            }
    return new int[]{(int) (xMin - this.xMin()), (int) (yMin - this.yMin())};
    }



 */

}
