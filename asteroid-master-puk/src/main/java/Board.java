import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedList;

public class Board extends JPanel implements Runnable {
    private Thread thread;
    private LinkedList<Asteroid> asteroids;
    private LinkedList<Bullet> bullets;
    private Player localPlayer;
    private Player remotePlayer;
    private int score = 0;
    //переменные изображения объекта
    private static BufferedImage backgroundImage;
    private static BufferedImage redPlayerImage;
    private static BufferedImage bluePlayerImage;
    private static BufferedImage[] asteroidImage = new BufferedImage[3];
    private static BufferedImage bulletImage;
    private boolean isPaused = true;
    private final float FPS;
    //Переменные для использования в соединении
    private final String serverIpAddress;
    private final int PORT = 40541;
    private Socket socket;
    private InputStream ois;
    private OutputStream oos;

    public Board(String serverIpAddress) {
        this.serverIpAddress = serverIpAddress;
        asteroids = new LinkedList<>();
        bullets = new LinkedList<>();
        setFocusable(true);
        setVisible(true);
        setPreferredSize(new Dimension(800, 600));
        addKeyboardListener();
        loadResources();
        setUpPlayers();
        FPS = 60;
        connectToServer();
    }

    private void loadResources() {
        backgroundImage = loadImage("space.png");
        redPlayerImage = loadImage("spaceship_red.png");
        bluePlayerImage = loadImage("spaceship_blue.png");
        asteroidImage[0] = loadImage("asteroid1.png");
        asteroidImage[1] = loadImage("asteroid2.png");
        asteroidImage[2] = loadImage("asteroid3.png");
        bulletImage = loadImage("projectile.png");
    }

    //Инициализировать переменные игрока
    private void setUpPlayers() {
        localPlayer = new Player(new Point(),
            new Dimension(bluePlayerImage.getWidth(), bluePlayerImage.getHeight()));
        remotePlayer = new Player(new Point(),
            new Dimension(redPlayerImage.getWidth(), redPlayerImage.getHeight()));
    }

    //Обработка событий клавиатуры
    private void addKeyboardListener() {
        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();

                if (keyCode == KeyEvent.VK_F2) {
                    startGame();
                }

                if (!isPaused && localPlayer.isAlive()) {
                    if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT) {
                        // Перемещает локального игрока и отправляет сообщение удаленному игроку, информируя его о
                        // местоположении.
                        if (keyCode == KeyEvent.VK_LEFT) {
                            localPlayer.getPosition().x -= 10;
                        } else {
                            localPlayer.getPosition().x += 10;
                        }

                        try {
                            MyPacket leftPacket = MyPacket.create(MyPacket.TYPE_MOVE);
                            leftPacket.setValue(1, localPlayer.getPosition().x);
                            leftPacket.setValue(2, localPlayer.getPosition().y);
                            oos.write(leftPacket.toByteArray());
                            oos.flush();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    } else if (keyCode == KeyEvent.VK_SPACE) { //Создает снаряд и отправляет сообщение
                        // удаленному игроку, информируя его о местоположении.
                        Point bulletPoint = new Point(localPlayer.getPosition().x
                                                      + localPlayer.getSize().width / 2,
                            localPlayer.getPosition().y);
                        fire(bulletPoint);
                        try {
                            MyPacket bulletPacket = MyPacket.create(3);
                            bulletPacket.setValue(1, bulletPoint.x);
                            bulletPacket.setValue(2, bulletPoint.y);
                            oos.write(bulletPacket.toByteArray());
                            oos.flush();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }
            }
        });
    }

    //создаем экземпляры игроков и запускаем основной поток
    private void startGame() {
        int height = (int)this.getPreferredSize().getHeight();
        int width = (int)this.getPreferredSize().getWidth();

        if (localPlayer.getId() == 0) {
            localPlayer.setPosition(new Point(100, height - localPlayer.getSize().height - 20));
            remotePlayer.setPosition(new Point(width - 100, height - remotePlayer.getSize().height - 20));
        } else {
            localPlayer.setPosition(new Point(width - 100, height - localPlayer.getSize().height - 20));
            remotePlayer.setPosition(new Point(100, height - remotePlayer.getSize().height - 20));
        }

        isPaused = false;

        if (thread == null) {
            thread = new Thread(this);
        }

        if (!thread.isAlive()) {
            thread.start();
        }
    }

    private void createAsteroid(Point pos, Point speed, int radius) {
        Dimension size = new Dimension(asteroidImage[radius].getWidth(), asteroidImage[radius].getHeight());
        asteroids.addFirst(new Asteroid(pos, speed, size, radius));
    }

    private void fire(Point pos) {
        bullets.add(new Bullet(pos, new Dimension(bulletImage.getWidth(), bulletImage.getHeight())));
    }

    //Подключиться к игровому серверу
    private void connectToServer() {
        try {
            socket = new Socket(serverIpAddress, PORT);
            oos = socket.getOutputStream();
            ois = socket.getInputStream();

            MyPacket packet = MyPacket.create(MyPacket.TYPE_OK);
            oos.write(packet.toByteArray());
            oos.flush();
            startListeningServer();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Ошибка подключения к серверу: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    //Запустить поток для асинхронного приема сообщений
    private void startListeningServer() {
        new Thread(() -> {
            try {
                while (true) {
                    byte[] data = readInput(ois);

                    MyPacket packet = MyPacket.parse(data);

                    switch (packet.getType()) {
                        case MyPacket.TYPE_ID -> {
                            System.out.println("Id: " + packet.getValue(1).toString());
                            localPlayer.setId(packet.getValue(1, Integer.class));
                        }
                        case MyPacket.TYPE_MOVE -> {
                            System.out.println("Move");
                            remotePlayer.setPosition(new Point(
                                    packet.getValue(1, Integer.class),
                                    packet.getValue(2, Integer.class)
                            ));
                        }
                        case MyPacket.TYPE_FIRE -> {
                            System.out.println("Fire");
                            fire(new Point(
                                    packet.getValue(1, Integer.class),
                                    packet.getValue(2, Integer.class)
                            ));
                        }
                        case MyPacket.TYPE_START -> {
                            System.out.println("Start");
                            startGame();
                        }
                        case MyPacket.TYPE_ASTEROID -> {
                            createAsteroid(
                                    new Point(
                                            packet.getValue(1, Integer.class),
                                            packet.getValue(2, Integer.class)
                                    ),
                                    new Point(
                                            packet.getValue(3, Integer.class),
                                            packet.getValue(4, Integer.class)
                                    ),
                                    packet.getValue(5, Integer.class)
                            );
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2d = (Graphics2D)g.create();

        g2d.drawImage(backgroundImage, null, 0, 0);

        if (isPaused) {
            g2d.setColor(Color.blue);
            g2d.setFont(new Font("Segoe UI Light", Font.BOLD, 36));
            g2d.drawString("В ожидании 2-го игрока.", this.getSize().width / 2 - 200, this.getSize().height / 2);
        } else {

            for (Asteroid a : asteroids) {
                g2d.drawImage(asteroidImage[a.getRadius()], a.getPosition().x, a.getPosition().y, null);
            }

            for (Bullet b : bullets) {
                g2d.drawImage(bulletImage, b.getPosition().x, b.getPosition().y, null);
            }

            if (localPlayer.isAlive()) {
                g2d.drawImage(bluePlayerImage, localPlayer.getPosition().x,
                    localPlayer.getPosition().y, null);
            }

            if (remotePlayer.isAlive()) {
                g2d.drawImage(redPlayerImage, remotePlayer.getPosition().x,
                    remotePlayer.getPosition().y, null);
            }

            g2d.setColor(new Color(128, 0, 255));
            g2d.setFont(new Font("Segoe UI Light", Font.BOLD, 36));
            g2d.drawString("Счет:" + score, this.getWidth() - 200, 50);
        }
    }

    @Override
    public void run() {
        while (!isPaused) {
            try {
                Thread.sleep((int)FPS);

                LinkedList<Asteroid> auxAsteroid = (LinkedList<Asteroid>)asteroids.clone();
                LinkedList<Bullet> auxBullet = (LinkedList<Bullet>)bullets.clone();

                for (Asteroid a : asteroids) {
                    //сдвинуть астероид вниз
                    a.getPosition().y += a.getSpeed().y;

                    //Если локальный игрок жив, проверить, не попал ли в него какой-либо астероид.
                    if (localPlayer.isAlive()) {
                        if (collisionDetection(a, localPlayer)) {
                            localPlayer.setAlive(false);
                        }
                    }

                    //Если удаленный игрок жив, провертьб, не попал ли в него какой-либо астероид.
                    if (remotePlayer.isAlive()) {
                        if (collisionDetection(a, remotePlayer)) {
                            remotePlayer.setAlive(false);
                        }
                    }

                    //Проверяет, попал ли какой-либо снаряд в какой-либо астероид.
                    for (Bullet b : bullets) {
                        if (collisionDetection(a, b)) {
                            auxAsteroid.remove(a);
                            auxBullet.remove(b);
                            score += 10;
                        }
                    }

                    if (a.getPosition().y > this.getHeight()) {
                        auxAsteroid.remove(a);
                    }
                }
                asteroids = auxAsteroid;

                for (Bullet b : bullets) {
                    b.getPosition().y -= b.getSpeed().y;
                    if (b.getPosition().y < 0) {
                        auxBullet.remove(b);
                    }
                }
                bullets = auxBullet;

                repaint();
            } catch (Exception e) {
            }
        }
    }

    //Возвращает изображения.
    public BufferedImage loadImage(String fileName) {
        try {
            System.out.println(getClass().getResource("/img/" + fileName));
            return ImageIO.read(getClass().getResource("/img/" + fileName));
        } catch (IOException e) {
            System.out.println("Content could not be read");
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void update(Graphics g) {
        super.paint(g);
        Image offScreen = null;
        Graphics offGraphics = null;
        Graphics2D g2d = (Graphics2D)g.create();
        Dimension dimension = getSize();
        offScreen = createImage(dimension.width, dimension.height);
        offGraphics = offScreen.getGraphics();
        offGraphics.setColor(getBackground());
        offGraphics.fillRect(0, 0, dimension.width, dimension.height);
        offGraphics.setColor(Color.black);
        paint(offGraphics);
        g2d.drawImage(offScreen, 0, 0, null);
        g2d.dispose();
    }

    //Обнаружение столкновения между двумя спрайтами
    private boolean collisionDetection(Sprite sprite1, Sprite sprite2) {
        Point pos1 = sprite1.getPosition();
        Point pos2 = sprite2.getPosition();
        int w1 = sprite1.getSize().width;
        int h1 = sprite1.getSize().height;
        int w2 = sprite2.getSize().width;
        int h2 = sprite2.getSize().height;
        return (((pos1.x > pos2.x && pos1.x < pos2.x + w2)
                 && (pos1.y > pos2.y && pos1.y < pos2.y + h2))
                || ((pos2.x > pos1.x && pos2.x < pos1.x + w1)
                    && (pos2.y > pos1.y && pos2.y < pos1.y + h1)));
    }

    private byte[] readInput(InputStream stream) throws IOException {
        int b;
        byte[] buffer = new byte[10];
        int counter = 0;
        while ((b = stream.read()) > -1) {
            buffer[counter++] = (byte)b;
            if (counter >= buffer.length) {
                buffer = extendArray(buffer);
            }
            if (counter > 2 && MyPacket.compareEOP(buffer, counter - 1)) {
                break;
            }
        }
        byte[] data = new byte[counter];
        System.arraycopy(buffer, 0, data, 0, counter);
        return data;
    }

    private byte[] extendArray(byte[] oldArray) {
        int oldSize = oldArray.length;
        byte[] newArray = new byte[oldSize * 2];
        System.arraycopy(oldArray, 0, newArray, 0, oldSize);
        return newArray;
    }
}
