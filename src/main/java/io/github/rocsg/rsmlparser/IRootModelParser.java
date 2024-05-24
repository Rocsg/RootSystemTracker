package io.github.rocsg.rsmlparser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface IRootModelParser {

    IRootModelParser createRootModel(IRootModelParser rootModel, float time);

    IRootModelParser createRootModels(Map<LocalDate, List<IRootModelParser>> rootModels, float scaleFactor);
}


// Metadata.java
class Metadata {
    public List<PropertyDefinition> propertiedef;
    float version;
    String unit;
    float resolution;
    LocalDate modifDate;
    String date2Use;
    String software;
    String user;
    String fileKey;

    public Metadata() {
        this.version = 0;
        this.unit = "";
        this.resolution = 0;
        this.modifDate = LocalDate.now();
        this.software = "";
        this.user = "";
        this.fileKey = "";
        this.date2Use = "";
        this.propertiedef = new ArrayList<>();
    }

    public Metadata(Metadata metadata) {
        this.version = metadata.version;
        this.unit = metadata.unit;
        this.resolution = metadata.resolution;
        this.modifDate = metadata.modifDate;
        this.software = metadata.software;
        this.user = metadata.user;
        this.fileKey = metadata.fileKey;
        this.propertiedef = new ArrayList<>();
        this.date2Use = metadata.date2Use;
        for (PropertyDefinition propertiedef : metadata.propertiedef) {
            this.propertiedef.add(new PropertyDefinition(propertiedef.label, propertiedef.type, propertiedef.unit));
        }
    }

    class PropertyDefinition {
        // Mapping label - type - unit
        public String label;
        public String type;
        public String unit;

        public PropertyDefinition() {
            this.label = "";
            this.type = "";
            this.unit = "";
        }

        public PropertyDefinition(String label, String type, String unit) {
            this.label = label;
            this.type = type;
            this.unit = unit;
        }
    }
}

