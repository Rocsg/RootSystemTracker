package io.github.rocsg.topologicaltracking;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TreeCC - Structure de données arborescente pour représenter l'organisation hiérarchique des Connected Components (CC).
 * Permet de naviguer dans l'arborescence : racines, chemins principaux, branches latérales, parents, enfants.
 */
public class TreeCC {
    
    /** Le CC correspondant à ce nœud de l'arbre */
    private CC cc;
    
    /** Le parent de ce nœud dans l'arbre (null pour les racines) */
    private TreeCC parent;
    
    /** Les enfants de ce nœud (branches partant de ce CC) */
    private List<TreeCC> children;
    
    /** L'index de la plante correspondante */
    private int plantIndex;
    
    /** L'ordre de la racine (1 = primaire, 2 = secondaire, etc.) */
    private int order;
    
    /** Position dans l'organe (0 = début de l'organe) */
    private int indexInOrgan;
    
    /** Indique si ce nœud est le début d'un organe */
    private boolean isOrganStart;
    
    /** Indique si ce nœud fait partie du chemin principal (trunk) */
    private boolean isTrunkNode;
    
    /**
     * Constructeur principal
     */
    public TreeCC(CC cc) {
        this.cc = cc;
        this.children = new ArrayList<>();
        this.parent = null;
        this.plantIndex = -1;
        this.order = 0;
        this.indexInOrgan = 0;
        this.isOrganStart = false;
        this.isTrunkNode = false;
    }
    
    /**
     * Constructeur avec informations complètes
     */
    public TreeCC(CC cc, int plantIndex, int order, int indexInOrgan, boolean isOrganStart, boolean isTrunkNode) {
        this.cc = cc;
        this.children = new ArrayList<>();
        this.parent = null;
        this.plantIndex = plantIndex;
        this.order = order;
        this.indexInOrgan = indexInOrgan;
        this.isOrganStart = isOrganStart;
        this.isTrunkNode = isTrunkNode;
    }
    
    // ==================== GETTERS ET SETTERS ====================
    
    public CC getCc() {
        return cc;
    }
    
    public void setCc(CC cc) {
        this.cc = cc;
    }
    
    public TreeCC getParent() {
        return parent;
    }
    
    public void setParent(TreeCC parent) {
        this.parent = parent;
    }
    
    public List<TreeCC> getChildren() {
        return children;
    }
    
    public int getPlantIndex() {
        return plantIndex;
    }
    
    public void setPlantIndex(int plantIndex) {
        this.plantIndex = plantIndex;
    }
    
    public int getOrder() {
        return order;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
    
    public int getIndexInOrgan() {
        return indexInOrgan;
    }
    
    public void setIndexInOrgan(int indexInOrgan) {
        this.indexInOrgan = indexInOrgan;
    }
    
    public boolean isOrganStart() {
        return isOrganStart;
    }
    
    public void setOrganStart(boolean isOrganStart) {
        this.isOrganStart = isOrganStart;
    }
    
    public boolean isTrunkNode() {
        return isTrunkNode;
    }
    
    public void setTrunkNode(boolean isTrunkNode) {
        this.isTrunkNode = isTrunkNode;
    }
    
    // ==================== MÉTHODES DE MANIPULATION DE L'ARBRE ====================
    
    /**
     * Ajoute un enfant à ce nœud et définit ce nœud comme parent de l'enfant
     */
    public void addChild(TreeCC child) {
        if (child != null && !children.contains(child)) {
            children.add(child);
            child.setParent(this);
        }
    }
    
    /**
     * Retire un enfant de ce nœud
     */
    public void removeChild(TreeCC child) {
        if (children.remove(child)) {
            child.setParent(null);
        }
    }
    
    /**
     * Vérifie si ce nœud est une racine (pas de parent)
     */
    public boolean isRoot() {
        return parent == null;
    }
    
    /**
     * Vérifie si ce nœud est une feuille (pas d'enfants)
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }
    
    /**
     * Retourne le nombre d'enfants
     */
    public int getChildrenCount() {
        return children.size();
    }
    
    /**
     * Vérifie si ce nœud a des enfants
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }
    
    // ==================== NAVIGATION DANS L'ARBRE ====================
    
    /**
     * Retourne la racine de cet arbre
     */
    public TreeCC getRoot() {
        TreeCC current = this;
        while (current.parent != null) {
            current = current.parent;
        }
        return current;
    }
    
    /**
     * Retourne le chemin complet depuis la racine jusqu'à ce nœud
     */
    public List<TreeCC> getPathFromRoot() {
        List<TreeCC> path = new ArrayList<>();
        TreeCC current = this;
        while (current != null) {
            path.add(0, current);
            current = current.parent;
        }
        return path;
    }
    
    /**
     * Retourne le chemin complet depuis ce nœud jusqu'à la racine
     */
    public List<TreeCC> getPathToRoot() {
        List<TreeCC> path = new ArrayList<>();
        TreeCC current = this;
        while (current != null) {
            path.add(current);
            current = current.parent;
        }
        return path;
    }
    
    /**
     * Retourne tous les descendants de ce nœud (parcours en profondeur)
     */
    public List<TreeCC> getAllDescendants() {
        List<TreeCC> descendants = new ArrayList<>();
        for (TreeCC child : children) {
            descendants.add(child);
            descendants.addAll(child.getAllDescendants());
        }
        return descendants;
    }
    
    /**
     * Retourne toutes les feuilles descendants de ce nœud
     */
    public List<TreeCC> getAllLeaves() {
        List<TreeCC> leaves = new ArrayList<>();
        if (isLeaf()) {
            leaves.add(this);
        } else {
            for (TreeCC child : children) {
                leaves.addAll(child.getAllLeaves());
            }
        }
        return leaves;
    }
    
    /**
     * Retourne la profondeur de ce nœud (0 pour la racine)
     */
    public int getDepth() {
        int depth = 0;
        TreeCC current = this;
        while (current.parent != null) {
            depth++;
            current = current.parent;
        }
        return depth;
    }
    
    /**
     * Retourne la hauteur de ce sous-arbre (0 pour une feuille)
     */
    public int getHeight() {
        if (isLeaf()) {
            return 0;
        }
        int maxHeight = 0;
        for (TreeCC child : children) {
            maxHeight = Math.max(maxHeight, child.getHeight());
        }
        return maxHeight + 1;
    }
    
    // ==================== NAVIGATION DANS LES ORGANES ====================
    
    /**
     * Retourne le chemin principal (trunk) auquel appartient ce nœud
     * Remonte jusqu'au début de l'organe (indexInOrgan == 0)
     */
    public List<TreeCC> getOrganPath() {
        List<TreeCC> organPath = new ArrayList<>();
        TreeCC current = this;
        
        // Remonter jusqu'au début de l'organe
        while (current != null && !current.isOrganStart) {
            current = current.parent;
        }
        
        if (current == null) {
            return organPath;
        }
        
        // Descendre le long du chemin principal
        organPath.add(current);
        TreeCC next = getNextInOrgan(current);
        while (next != null) {
            organPath.add(next);
            next = getNextInOrgan(next);
        }
        
        return organPath;
    }
    
    /**
     * Retourne le prochain nœud dans l'organe (suit le chemin principal)
     */
    private TreeCC getNextInOrgan(TreeCC node) {
        for (TreeCC child : node.children) {
            if (child.order == node.order && child.indexInOrgan == node.indexInOrgan + 1) {
                return child;
            }
        }
        return null;
    }
    
    /**
     * Retourne tous les enfants qui sont des débuts d'organes latéraux
     */
    public List<TreeCC> getLateralOrganStarts() {
        return children.stream()
                .filter(TreeCC::isOrganStart)
                .filter(child -> child.order > this.order)
                .collect(Collectors.toList());
    }
    
    /**
     * Retourne tous les organes latéraux (racines secondaires) issus de ce nœud ou de ses descendants
     * ATTENTION : Cette méthode est récursive et retourne TOUS les organes latéraux de tous les ordres
     */
    public List<List<TreeCC>> getAllLateralOrgans() {
        List<List<TreeCC>> lateralOrgans = new ArrayList<>();
        
        for (TreeCC child : children) {
            if (child.isOrganStart && child.order > this.order) {
                lateralOrgans.add(child.getOrganPath());
            }
            // Récursif pour obtenir aussi les latérales des latérales
            lateralOrgans.addAll(child.getAllLateralOrgans());
        }
        
        return lateralOrgans;
    }
    
    /**
     * Retourne uniquement les organes latéraux qui émergent DIRECTEMENT du chemin principal de cet organe.
     * Contrairement à getAllLateralOrgans(), cette méthode n'est PAS récursive et ne retourne que les 
     * organes de l'ordre immédiatement supérieur branchés sur le chemin principal.
     * 
     * Par exemple, si this est le début d'un organe d'ordre 1 (racine primaire) :
     * - Retournera toutes les racines d'ordre 2 (secondaires) branchées sur cette primaire
     * - Ne retournera PAS les racines d'ordre 3 (tertiaires) qui émergent des secondaires
     * 
     * Schéma explicatif :
     * <pre>
     * Primaire (ordre 1):  ●───●───●───●───●───●───●───●
     *                           │       │       │
     *                      Sec1 ●   Sec2 ●   Sec3 ●  (ordre 2 - RETOURNÉES)
     *                           │       │       │
     *                      Ter1 ●  Ter2 ●  Ter3 ●     (ordre 3 - NON RETOURNÉES)
     * 
     * getAllLateralOrgans() retournerait : [Sec1, Sec2, Sec3, Ter1, Ter2, Ter3]  (6 organes)
     * getAllLateralOrgansEmergingDirectlyFromThisOrganPath() retournerait : [Sec1, Sec2, Sec3]  (3 organes)
     * </pre>
     * 
     * @return Liste des chemins des organes latéraux directs (un chemin = une liste de TreeCC)
     */
    public List<List<TreeCC>> getAllLateralOrgansEmergingDirectlyFromThisOrganPath() {
        List<List<TreeCC>> lateralOrgans = new ArrayList<>();
        
        // Obtenir le chemin complet de cet organe
        List<TreeCC> organPath = this.getOrganPath();
        
        // Pour chaque nœud du chemin principal de cet organe
        for (TreeCC nodeOnPath : organPath) {
            // Parcourir les enfants de ce nœud
            for (TreeCC child : nodeOnPath.children) {
                // Si c'est un début d'organe latéral (ordre supérieur)
                if (child.isOrganStart && child.order > nodeOnPath.order) {
                    // Ajouter le chemin complet de cet organe latéral
                    lateralOrgans.add(child.getOrganPath());
                }
            }
        }
        
        return lateralOrgans;
    }
    
    // ==================== MÉTHODES UTILITAIRES ====================
    
    /**
     * Compte le nombre total de pixels dans ce sous-arbre
     */
    public int getTotalPixelCount() {
        int count = cc.nPixels;
        for (TreeCC child : children) {
            count += child.getTotalPixelCount();
        }
        return count;
    }
    
    /**
     * Compte le nombre de nœuds dans ce sous-arbre
     */
    public int getNodeCount() {
        int count = 1;
        for (TreeCC child : children) {
            count += child.getNodeCount();
        }
        return count;
    }
    
    /**
     * Retourne une représentation textuelle du nœud
     */
    @Override
    public String toString() {
        return String.format("TreeCC[Plant=%d, Order=%d, IndexInOrgan=%d, CC=%s, Children=%d]",
                plantIndex, order, indexInOrgan, 
                cc != null ? cc.toString() : "null", 
                children.size());
    }
    
    /**
     * Affiche l'arbre complet (pour debug)
     */
    public void printTree(String prefix, boolean isTail) {
        System.out.println(prefix + (isTail ? "└── " : "├── ") + this.toString());
        for (int i = 0; i < children.size(); i++) {
            children.get(i).printTree(prefix + (isTail ? "    " : "│   "), i == children.size() - 1);
        }
    }
    
    /**
     * Affiche l'arbre complet depuis la racine
     */
    public void printTree() {
        System.out.println("=== Tree Structure ===");
        printTree("", true);
    }
}
