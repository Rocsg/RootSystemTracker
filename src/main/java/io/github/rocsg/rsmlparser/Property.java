package io.github.rocsg.rsmlparser;

// Define other classes used in Root4Parser
public class Property {
    private final String name;
    private final double value;

    public Property(String name, String value) {
        this.name = name;
        this.value = Double.parseDouble(value);
    }

    public String getName() {
        return name;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Property{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}
