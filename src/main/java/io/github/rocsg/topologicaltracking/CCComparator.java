package io.github.rocsg.topologicaltracking;

public class CCComparator implements java.util.Comparator {
    public int compare(Object o1, Object o2) {
        return ((Double) ((CC) o1).x).compareTo(((CC) o2).x);
    }
}