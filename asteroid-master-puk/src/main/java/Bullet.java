import java.awt.*;

class Bullet extends Sprite {

    public Bullet(Point position, Dimension size) {
        this.setPosition(position);
        this.setSize(size);
        this.setSpeed(new Point(0, 10));
    }
}
