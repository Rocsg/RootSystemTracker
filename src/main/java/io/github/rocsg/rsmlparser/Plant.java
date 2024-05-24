package io.github.rocsg.rsmlparser;

import java.util.ArrayList;
import java.util.List;

// Plant.java
public class Plant {
    final List<Root4Parser> roots;

    public Plant() {
        this.roots = new ArrayList<>();
    }

    public void addRoot(Root4Parser root) {
        this.roots.add(root);
    }

    public List<String> getListID() {
        List<String> listID = new ArrayList<>();
        for (Root4Parser root : roots) {
            listID.add(root.id);
        }
        return listID;
    }

    public Root4Parser getRootByID(String id) {
        for (Root4Parser root : roots) {
            if (root.id.equals(id)) {
                return root;
            }
        }
        return null;
    }

    public List<Root4Parser> getRoots() {
        return roots;
    }

    public List<Root4Parser> getFirstOrderRoots() {
        List<Root4Parser> firstOrderRoots = new ArrayList<>();
        for (Root4Parser root : roots) {
            if (root.getOrder() == 1) {
                firstOrderRoots.add(root);
            }
        }
        return firstOrderRoots;
    }

    @Override
    public String toString() {
        return "\nPlant{" +
                "roots=" + roots +
                "}\n";
    }
} // TODO : strong assumption : No new plant is created in the same scene
