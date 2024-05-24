package io.github.rocsg.rsmlparser;

import io.github.rocsg.rsml.RootModel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RootModel4Parser implements IRootModelParser {
    public final List<Scene> scenes;
    public Metadata metadatas;

    public RootModel4Parser() {
        this.scenes = new ArrayList<>();
        this.metadatas = new Metadata();
    }

    public RootModel4Parser(Metadata metadata) {
        this.scenes = new ArrayList<>();
        this.metadatas = metadata;
    }

    public void addScene(Scene scene) {
        this.scenes.add(scene);
    }

    // get metadata elements
    public float getVersion() {
        return metadatas.version;
    }

    public String getUnit() {
        return metadatas.unit;
    }

    public float getResolution() {
        return metadatas.resolution;
    }

    public LocalDate getModifDate() {
        return metadatas.modifDate;
    }

    public String getSoftware() {
        return metadatas.software;
    }

    public String getUser() {
        return metadatas.user;
    }

    public String getFileKey() {
        return metadatas.fileKey;
    }

    public List<Scene> getScenes() {
        return scenes;
    }

    @Override
    public String toString() {
        return "RootModel4Parser{" +
                "scenes=" + scenes +
                '}';
    }

    @Override
    public IRootModelParser createRootModel(IRootModelParser rootModel, float time) {
        if (rootModel instanceof RootModel4Parser) {
            return rootModel;
        } else if (rootModel instanceof RootModel) {
            return new RootModel4Parser();
        }
        return null;
    }

    @Override
    public IRootModelParser createRootModels(Map<LocalDate, List<IRootModelParser>> rootModels, float scaleFactor) {
        return null;
    }

}
