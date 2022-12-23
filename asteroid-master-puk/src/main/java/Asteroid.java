import java.awt.*;

class Asteroid extends Sprite {

    private int radius = 0;

    public Asteroid(Point position, Dimension size) {
        this.setPosition(position);
        this.setSize(size);
        this.setSpeed(new Point(0, 5));
    }

    public Asteroid(Point position, Point speed, Dimension size, int radius) {
        this(position, size);
        this.setSpeed(speed);
        this.setRadius(radius);
    }

    public int getRadius() {
        return this.radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }
}
