package io.github.rocsg.rsmlparser;

import java.util.ArrayList;
import java.util.List;

// Scene.java
public class Scene {
    private final List<Plant> plants;

    public Scene() {
        this.plants = new ArrayList<>();
    }

    public void addPlant(Plant plant) {
        this.plants.add(plant);
    }

    public List<Plant> getPlants() {
        return plants;
    }

    @Override
    public String toString() {
        return "Scene{" +
                "plants=" + plants +
                '}';
    }
}
