package io.github.rocsg.rstdev;

import com.jogamp.nativewindow.util.Rectangle;

public class TestRomain {
    public static void main(String[] args) {
        Rectangle ra=new Rectangle();
        Rectangle rb=ra;
        Rectangle rc=ra;
        System.out.println((rb==rc));
    }
}
