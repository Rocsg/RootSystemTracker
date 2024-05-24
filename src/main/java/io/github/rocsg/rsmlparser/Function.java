package io.github.rocsg.rsmlparser;

import java.util.ArrayList;
import java.util.List;

public class Function {
    private final String name;
    private final List<Double> samples;

    public Function(String name) {
        this.name = name;
        this.samples = new ArrayList<>();
    }

    public void addSample(String sample) {
        this.samples.add(Double.parseDouble(sample));
    }

    public String getName() {
        return name;
    }

    public List<Double> getSamples() {
        return samples;
    }

    @Override
    public String toString() {
        return "Function{" +
                "name='" + name + '\'' +
                ", samples=" + samples +
                '}';
    }
}
