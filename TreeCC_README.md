# TreeCC - Structure arborescente pour le tracking de racines

## Vue d'ensemble

`TreeCC` est une structure de données qui représente l'organisation hiérarchique des composantes connectées (CC) dans le système racinaire. Elle permet de naviguer facilement dans l'arborescence des racines primaires, secondaires, tertiaires, etc.

## Fonctionnalités principales

### 1. Construction automatique

La classe `TreeCC` est automatiquement construite dans la fonction `buildStep5PruneOutliersFirstPhase()` :

```java
List<TreeCC> treeRoots = buildStep5PruneOutliersFirstPhase(graph, pph);
```

Cette fonction retourne une liste de racines TreeCC, une pour chaque plante identifiée.

### 2. Navigation dans l'arbre

#### Obtenir les enfants d'un nœud
```java
List<TreeCC> children = node.getChildren();
```

#### Obtenir le parent d'un nœud
```java
TreeCC parent = node.getParent();
```

#### Vérifier si c'est une racine ou une feuille
```java
boolean isRoot = node.isRoot();
boolean isLeaf = node.isLeaf();
```

#### Remonter jusqu'à la racine
```java
TreeCC root = node.getRoot();
List<TreeCC> pathToRoot = node.getPathToRoot();
```

### 3. Parcours de l'arborescence

#### Obtenir tous les descendants
```java
List<TreeCC> allDescendants = node.getAllDescendants();
```

#### Obtenir toutes les feuilles
```java
List<TreeCC> leaves = node.getAllLeaves();
```

#### Obtenir toutes les racines latérales (RÉCURSIF - tous ordres)
```java
// Retourne TOUS les organes latéraux, y compris les tertiaires, quaternaires, etc.
List<List<TreeCC>> allLateralOrgans = node.getAllLateralOrgans();
```

#### Obtenir les racines latérales directes (NON RÉCURSIF - ordre immédiatement supérieur)
```java
// Retourne UNIQUEMENT les organes branchés directement sur le chemin principal
// Par exemple, pour une racine primaire (ordre 1) : seulement les secondaires (ordre 2)
List<List<TreeCC>> directLateralOrgans = node.getAllLateralOrgansEmergingDirectlyFromThisOrganPath();
```

**Différence importante :**
- `getAllLateralOrgans()` : **RÉCURSIF** - retourne TOUS les organes latéraux de tous niveaux
  - Exemple : pour une primaire, retourne les secondaires (ordre 2) + tertiaires (ordre 3) + ...
- `getAllLateralOrgansEmergingDirectlyFromThisOrganPath()` : **NON RÉCURSIF** - retourne uniquement les organes directement branchés
  - Exemple : pour une primaire, retourne SEULEMENT les secondaires (ordre 2)

### 4. Informations sur les nœuds

Chaque `TreeCC` contient :
- `cc` : le Connected Component (CC) correspondant
- `plantIndex` : l'index de la plante
- `order` : l'ordre de la racine (1=primaire, 2=secondaire, etc.)
- `indexInOrgan` : position dans l'organe (0=début)
- `isOrganStart` : indique si c'est le début d'un organe
- `isTrunkNode` : indique si c'est sur le chemin principal (trunk)

### 5. Statistiques

```java
int nodeCount = tree.getNodeCount();
int totalPixels = tree.getTotalPixelCount();
int height = tree.getHeight();
int depth = node.getDepth();
```

## Exemples d'utilisation

### Exemple 1 : Parcourir le chemin principal (trunk) de chaque plante

```java
List<TreeCC> treeRoots = buildStep5PruneOutliersFirstPhase(graph, pph);

for (int i = 0; i < treeRoots.size(); i++) {
    TreeCC root = treeRoots.get(i);
    System.out.println("Plante " + i + ":");
    
    TreeCC current = root;
    while (current != null) {
        System.out.println("  Position " + current.getIndexInOrgan() + 
            ": " + current.getCc());
        
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
}
```

### Exemple 2 : Lister les racines latérales (directes vs tous niveaux)

```java
for (TreeCC root : treeRoots) {
    // Méthode 1 : Récupérer TOUTES les latérales (récursif)
    List<List<TreeCC>> allLaterals = root.getAllLateralOrgans();
    System.out.println("Total latérales (tous ordres) : " + allLaterals.size());
    
    // Méthode 2 : Récupérer SEULEMENT les latérales directes (non récursif)
    List<List<TreeCC>> directLaterals = root.getAllLateralOrgansEmergingDirectlyFromThisOrganPath();
    System.out.println("Latérales directes (ordre 2 uniquement) : " + directLaterals.size());
    
    // Exemple : si la primaire a 5 secondaires, et ces secondaires ont 20 tertiaires au total
    // allLaterals.size() = 25 (5 secondaires + 20 tertiaires)
    // directLaterals.size() = 5 (seulement les secondaires)
    
    for (List<TreeCC> organ : directLaterals) {
        TreeCC organStart = organ.get(0);
        TreeCC attachmentPoint = organStart.getParent();
        
        System.out.println("  Ordre " + organStart.getOrder() + 
            ", attachée au segment " + attachmentPoint.getIndexInOrgan() +
            ", longueur : " + organ.size() + " segments");
    }
}
```

### Exemple 3 : Conversion vers RootModel

```java
// Dans buildStep9RefinePlongement, on peut maintenant utiliser TreeCC :

List<TreeCC> treeRoots = buildStep5PruneOutliersFirstPhase(graph, pph);

for (int plantIndex = 0; plantIndex < treeRoots.size(); plantIndex++) {
    TreeCC root = treeRoots.get(plantIndex);
    
    // 1. Créer la racine primaire (trunk)
    Root primaryRoot = createRootFromTreePath(getMainTrunkPath(root));
    rm.addRoot(primaryRoot);
    
    // 2. Créer les racines latérales et les attacher
    List<List<TreeCC>> lateralOrgans = root.getAllLateralOrgans();
    for (List<TreeCC> lateralPath : lateralOrgans) {
        TreeCC lateralStart = lateralPath.get(0);
        TreeCC attachmentPoint = lateralStart.getParent();
        
        Root lateralRoot = createRootFromTreePath(lateralPath);
        
        // Attacher au bon parent selon l'ordre
        Root parentRoot = findParentRoot(attachmentPoint, primaryRoot);
        parentRoot.addLateralRoot(lateralRoot, attachmentPoint.getIndexInOrgan());
    }
}
```

## Méthodes utilitaires

### Affichage de l'arbre (debug)

```java
// Afficher toute la structure de l'arbre
root.printTree();

// Ou avec plus de contrôle
root.printTree("", true);
```

### Exemple de sortie :
```
=== Tree Structure ===
└── TreeCC[Plant=0, Order=1, IndexInOrgan=0, CC=..., Children=3]
    ├── TreeCC[Plant=0, Order=1, IndexInOrgan=1, CC=..., Children=2]
    │   ├── TreeCC[Plant=0, Order=1, IndexInOrgan=2, CC=..., Children=1]
    │   └── TreeCC[Plant=0, Order=2, IndexInOrgan=0, CC=..., Children=0]
    └── TreeCC[Plant=0, Order=2, IndexInOrgan=0, CC=..., Children=0]
```

## Intégration avec le pipeline existant

La structure `TreeCC` est construite automatiquement pendant `buildStep5PruneOutliersFirstPhase()` et peut être utilisée dans les étapes suivantes, notamment `buildStep9RefinePlongement()` pour simplifier la construction du `RootModel`.

### Avantages

1. **Navigation facile** : Plus besoin de parcourir manuellement le graphe pour trouver les parents/enfants
2. **Structure claire** : L'arborescence reflète exactement l'organisation des racines
3. **Réutilisable** : Peut servir pour d'autres analyses (statistiques, visualisation, etc.)
4. **Performances** : La Map interne permet un accès O(1) depuis un CC vers son TreeCC

## Fichiers concernés

- `TreeCC.java` : Classe principale
- `TreeCCExample.java` : Exemples d'utilisation
- `RegionAdjacencyGraphPipelineV2.java` : Construction dans `buildStep5PruneOutliersFirstPhase()`

## Voir aussi

Consultez `TreeCCExample.java` pour des exemples complets et détaillés d'utilisation.
