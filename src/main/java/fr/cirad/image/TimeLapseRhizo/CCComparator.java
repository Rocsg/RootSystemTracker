package fr.cirad.image.TimeLapseRhizo;

public class CCComparator implements java.util.Comparator {
   public int compare(Object o1, Object o2) {
      return ((Double) ((CC) o1).x).compareTo((Double)((CC) o2).x);
   }
}