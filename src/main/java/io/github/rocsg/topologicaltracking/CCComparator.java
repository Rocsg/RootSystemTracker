package io.github.rocsg.topologicaltracking;

public class CCComparator implements java.util.Comparator {
    public int compare(Object o1, Object o2) {
        return (new Double(((CC) o1).xCentralPixAbsolu).compareTo(new Double(((CC) o2).xCentralPixAbsolu)));
    }
}