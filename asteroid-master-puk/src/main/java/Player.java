import java.awt.*;

class Player extends Sprite {

    private int id;
    private boolean isAlive;

    public Player(Point position, Dimension size) {
        this.setPosition(position);
        this.setSize(size);
        this.isAlive = true;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isAlive() {
        return this.isAlive;
    }

    public void setAlive(boolean isAlive) {
        this.isAlive = isAlive;
    }
}
