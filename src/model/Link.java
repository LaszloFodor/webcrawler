package model;

public record Link(String label, String url) implements Comparable<Link> {
    @Override
    public int compareTo(Link other) {
        return this.label.compareToIgnoreCase(other.label);
    }
}