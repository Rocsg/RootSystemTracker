package io.github.rocsg.topologicaltracking;

import java.util.List;

/**
 * Exemples d'utilisation de la classe TreeCC
 * 
 * Cette classe montre comment naviguer dans l'arborescence construite 
 * par buildStep5PruneOutliersFirstPhase
 */
public class TreeCCExample {

    /**
     * Exemple 1: Parcourir tous les chemins principaux de chaque plante
     */
    public static void exampleTraverseMainPaths(List<TreeCC> treeRoots) {
        System.out.println("=== Parcours des chemins principaux ===");
        
        for (int i = 0; i < treeRoots.size(); i++) {
            TreeCC root = treeRoots.get(i);
            System.out.println("\nPlante " + i + ":");
            
            // Parcourir le chemin principal (trunk)
            TreeCC current = root;
            int position = 0;
            while (current != null) {
                System.out.println("  Position " + position + ": " + current.getCc());
                
                // Chercher le prochain nœud du trunk (ordre 1)
                TreeCC next = null;
                for (TreeCC child : current.getChildren()) {
                    if (child.getOrder() == 1) {
                        next = child;
                        break;
                    }
                }
                current = next;
                position++;
            }
        }
    }

    /**
     * Exemple 2: Identifier toutes les racines latérales (ordre 2+) de chaque plante
     */
    public static void exampleFindLateralRoots(List<TreeCC> treeRoots) {
        System.out.println("\n=== Identification des racines latérales ===");
        
        for (int i = 0; i < treeRoots.size(); i++) {
            TreeCC root = treeRoots.get(i);
            System.out.println("\nPlante " + i + ":");
            
            // Obtenir toutes les racines latérales de tous ordres (RÉCURSIF)
            List<List<TreeCC>> allLateralOrgans = root.getAllLateralOrgans();
            System.out.println("  Nombre TOTAL de racines latérales (tous ordres): " + allLateralOrgans.size());
            
            // Obtenir uniquement les racines latérales directes (NON RÉCURSIF)
            List<List<TreeCC>> directLateralOrgans = root.getAllLateralOrgansEmergingDirectlyFromThisOrganPath();
            System.out.println("  Nombre de racines latérales DIRECTES (ordre 2 uniquement): " + directLateralOrgans.size());
            
            for (int j = 0; j < allLateralOrgans.size(); j++) {
                List<TreeCC> organ = allLateralOrgans.get(j);
                if (!organ.isEmpty()) {
                    TreeCC organStart = organ.get(0);
                    System.out.println("    Racine latérale " + j + 
                        " (ordre " + organStart.getOrder() + "): " + 
                        organ.size() + " segments, " +
                        "commence à " + organStart.getCc());
                }
            }
        }
    }

    /**
     * Exemple 2b: Comparer getAllLateralOrgans vs getAllLateralOrgansEmergingDirectlyFromThisOrganPath
     */
    public static void exampleCompareLateralMethods(List<TreeCC> treeRoots) {
        System.out.println("\n=== Comparaison des méthodes de récupération des latérales ===");
        
        for (int i = 0; i < treeRoots.size(); i++) {
            TreeCC root = treeRoots.get(i);
            System.out.println("\nPlante " + i + " (Racine primaire d'ordre 1):");
            
            // Méthode 1 : RÉCURSIVE - Tout l'arbre
            List<List<TreeCC>> allLaterals = root.getAllLateralOrgans();
            System.out.println("  getAllLateralOrgans() [RÉCURSIF] : " + allLaterals.size() + " organes");
            for (List<TreeCC> organ : allLaterals) {
                if (!organ.isEmpty()) {
                    TreeCC start = organ.get(0);
                    System.out.println("    - Ordre " + start.getOrder() + ", " + organ.size() + " segments");
                }
            }
            
            // Méthode 2 : NON RÉCURSIVE - Seulement les directs
            List<List<TreeCC>> directLaterals = root.getAllLateralOrgansEmergingDirectlyFromThisOrganPath();
            System.out.println("  getAllLateralOrgansEmergingDirectlyFromThisOrganPath() [NON RÉCURSIF] : " + directLaterals.size() + " organes");
            for (List<TreeCC> organ : directLaterals) {
                if (!organ.isEmpty()) {
                    TreeCC start = organ.get(0);
                    System.out.println("    - Ordre " + start.getOrder() + ", " + organ.size() + " segments");
                }
            }
            
            System.out.println("  => Différence : " + (allLaterals.size() - directLaterals.size()) + 
                " organes latéraux de niveau supérieur (ordre 3+)");
        }
    }

    /**
     * Exemple 3: Pour chaque CC, trouver son parent dans l'arborescence
     */
    public static void exampleFindParents(List<TreeCC> treeRoots) {
        System.out.println("\n=== Recherche de parents ===");
        
        for (int i = 0; i < treeRoots.size(); i++) {
            TreeCC root = treeRoots.get(i);
            System.out.println("\nPlante " + i + ":");
            
            // Parcourir tous les descendants
            List<TreeCC> allNodes = root.getAllDescendants();
            allNodes.add(0, root); // Ajouter la racine
            
            for (TreeCC node : allNodes) {
                if (node.getParent() != null) {
                    System.out.println("  " + node.getCc() + 
                        " -> parent: " + node.getParent().getCc());
                } else {
                    System.out.println("  " + node.getCc() + " -> RACINE (pas de parent)");
                }
            }
        }
    }

    /**
     * Exemple 4: Parcourir l'arbre en profondeur pour analyser la structure
     */
    public static void exampleDepthFirstTraversal(List<TreeCC> treeRoots) {
        System.out.println("\n=== Parcours en profondeur ===");
        
        for (int i = 0; i < treeRoots.size(); i++) {
            TreeCC root = treeRoots.get(i);
            System.out.println("\nPlante " + i + ":");
            depthFirstHelper(root, 0);
        }
    }
    
    private static void depthFirstHelper(TreeCC node, int depth) {
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indentBuilder.append("  ");
        }
        String indent = indentBuilder.toString();
        System.out.println(indent + "- Ordre " + node.getOrder() + 
            ", Position " + node.getIndexInOrgan() + 
            ": " + node.getCc());
        
        for (TreeCC child : node.getChildren()) {
            depthFirstHelper(child, depth + 1);
        }
    }

    /**
     * Exemple 5: Calculer des statistiques sur l'arborescence
     */
    public static void exampleComputeStatistics(List<TreeCC> treeRoots) {
        System.out.println("\n=== Statistiques des arbres ===");
        
        for (int i = 0; i < treeRoots.size(); i++) {
            TreeCC root = treeRoots.get(i);
            
            System.out.println("\nPlante " + i + ":");
            System.out.println("  Nombre total de nœuds: " + root.getNodeCount());
            System.out.println("  Nombre de feuilles: " + root.getAllLeaves().size());
            System.out.println("  Hauteur de l'arbre: " + root.getHeight());
            System.out.println("  Pixels totaux: " + root.getTotalPixelCount());
            
            // Compter les organes par ordre
            List<TreeCC> allNodes = root.getAllDescendants();
            allNodes.add(root);
            
            int[] orderCounts = new int[10]; // Jusqu'à l'ordre 9
            for (TreeCC node : allNodes) {
                if (node.getOrder() < orderCounts.length) {
                    orderCounts[node.getOrder()]++;
                }
            }
            
            System.out.println("  Distribution par ordre:");
            for (int order = 1; order < orderCounts.length; order++) {
                if (orderCounts[order] > 0) {
                    System.out.println("    Ordre " + order + ": " + orderCounts[order] + " segments");
                }
            }
        }
    }

    /**
     * Exemple 6: Retrouver le chemin complet d'un nœud jusqu'à la racine
     */
    public static void exampleGetPathToRoot(TreeCC node) {
        System.out.println("\n=== Chemin vers la racine ===");
        
        List<TreeCC> path = node.getPathToRoot();
        System.out.println("Chemin depuis " + node.getCc() + " vers la racine:");
        
        for (int i = 0; i < path.size(); i++) {
            TreeCC step = path.get(i);
            System.out.println("  " + i + ". Ordre " + step.getOrder() + 
                ", Position " + step.getIndexInOrgan() + 
                ": " + step.getCc());
        }
    }

    /**
     * Exemple 7: Utilisation pratique - conversion vers RootModel
     * 
     * Cette méthode montre comment on pourrait utiliser TreeCC pour faciliter
     * la construction d'un RootModel dans buildStep9RefinePlongement
     */
    public static void exampleConversionToRootModel(List<TreeCC> treeRoots) {
        System.out.println("\n=== Exemple de conversion vers RootModel ===");
        
        for (int plantIndex = 0; plantIndex < treeRoots.size(); plantIndex++) {
            TreeCC root = treeRoots.get(plantIndex);
            
            System.out.println("\nTraitement de la plante " + plantIndex + ":");
            
            // 1. Traiter le chemin principal (trunk, ordre 1)
            System.out.println("  Création de la racine primaire (trunk)...");
            List<TreeCC> trunkPath = getMainTrunkPath(root);
            System.out.println("    -> " + trunkPath.size() + " segments dans le trunk");
            
            // 2. Traiter toutes les racines latérales
            List<List<TreeCC>> lateralOrgans = root.getAllLateralOrgans();
            System.out.println("  Création de " + lateralOrgans.size() + " racines latérales...");
            
            for (int latIndex = 0; latIndex < lateralOrgans.size(); latIndex++) {
                List<TreeCC> lateralPath = lateralOrgans.get(latIndex);
                if (!lateralPath.isEmpty()) {
                    TreeCC lateralStart = lateralPath.get(0);
                    
                    // Trouver le point d'attache sur le parent
                    TreeCC attachmentPoint = lateralStart.getParent();
                    
                    System.out.println("    Latérale " + latIndex + 
                        " (ordre " + lateralStart.getOrder() + "): " +
                        lateralPath.size() + " segments, " +
                        "attachée au segment " + attachmentPoint.getIndexInOrgan() + 
                        " de l'ordre " + attachmentPoint.getOrder());
                }
            }
        }
    }
    
    /**
     * Helper: Récupère le chemin complet du trunk (ordre 1 uniquement)
     */
    private static List<TreeCC> getMainTrunkPath(TreeCC root) {
        List<TreeCC> path = new java.util.ArrayList<>();
        TreeCC current = root;
        
        while (current != null) {
            path.add(current);
            
            // Chercher le prochain nœud du trunk (ordre 1)
            TreeCC next = null;
            for (TreeCC child : current.getChildren()) {
                if (child.getOrder() == 1) {
                    next = child;
                    break;
                }
            }
            current = next;
        }
        
        return path;
    }
}
