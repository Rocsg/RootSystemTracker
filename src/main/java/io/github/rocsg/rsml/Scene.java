package io.github.rocsg.rsml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Scene class represents a scene containing multiple plants.
 */
public class Scene {
    private final List<Plant> plants; // List of plants in the scene

    /**
     * Constructs a Scene object with an empty list of plants.
     */
    public Scene() {
        this.plants = new ArrayList<>();
    }

    /**
     * Adds a plant to the scene.
     *
     * @param plant The plant to add.
     */
    public void addPlant(Plant plant) {
        this.plants.add(plant);
    }

    /**
     * Gets the list of plants in the scene.
     *
     * @return The list of plants.
     */
    public List<Plant> getPlants() {
        return plants;
    }

    /**
     * Returns a string representation of the Scene object.
     *
     * @return A string representation of the Scene object.
     */
    @Override
    public String toString() {
        return "Scene{" +
                "plants=" + plants +
                '}';
    }
}