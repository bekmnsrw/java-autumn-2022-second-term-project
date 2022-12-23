import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server extends JFrame {
    private ServerSocket server = null;
    private final int PORT = 40541;
    private final int MAX_CONNECTED_USERS = 2;
    private JTextArea console;
    private HashMap<Integer, OutputStream> oosList;
    private final Socket[] clients = new Socket[MAX_CONNECTED_USERS];

    public Server() {
        super("Сервер астероидов");
        initComponents();
        startServer();
    }

    private void initComponents() {
        setPreferredSize(new Dimension(250, 400));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        console = new JTextArea();
        console.setLineWrap(true);
        console.setEditable(false);
        add(console, BorderLayout.CENTER);

        pack();
        setVisible(true);
    }

    private void startServer() {
        try {
            oosList = new HashMap<>();
            console.append("Стартовый сервер.\n");
            console.append("Ожидание соединений.\n");
            server = new ServerSocket(PORT);

            for (int i = 0; i < MAX_CONNECTED_USERS; i++) {
                clients[i] = server.accept();
                Socket client = clients[i];

                String name = client.getInetAddress().getHostName();
                console.append(name + " только что подключился.\n");

                oosList.put(i, client.getOutputStream());

                //Сообщает идентификатор вашего клиента, чтобы узнать, является ли он игроком 1 или 2
                MyPacket packet = MyPacket.create(MyPacket.TYPE_ID);

                packet.setValue(1, i);
                oosList.get(i).write(packet.toByteArray());
                oosList.get(i).flush();

                new ServerThread(i).start();
            }

            for (int i = 0; i < MAX_CONNECTED_USERS; i++) {
                MyPacket packet = MyPacket.create(MyPacket.TYPE_START);
                oosList.get(i).write(packet.toByteArray());
                oosList.get(i).flush();
            }

            new GameTasks();

            console.append("Достигнуто максимальное количество подключенных игроков\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class ServerThread extends Thread {
        private int id;

        public ServerThread(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try {
                InputStream input = clients[id].getInputStream();

                while (true) {
                    byte[] data = readInput(input);
                    System.out.println(Arrays.toString(data));
                    for (int i : oosList.keySet()) {
                        if (i != id && oosList.containsKey(i)) {
                            oosList.get(i).write(data);
                            oosList.get(i).flush();
                        }
                    }
                }
            } catch (Exception ex) {
                console.append("Клиент: " + id + " был отсоединен.\n");
                disconnect();
            }
        }

        private void disconnect() {
            try {
                oosList.get(id).close();
            } catch (IOException ex) {
                console.append(ex.getMessage() + "\n");
            }
            oosList.remove(id);
        }
    }

    //
    private class GameTasks {

        private Random r = new Random();

        //Задержка по умолчанию в миллисекундах для запуска каждой задачи.
        final private int defaultDelay = 2000;
        //Время между созданием астероида
        final private int asteroidInterval = 600;

        private int asteroidTimeCounter = 0;

        private int asteroidSpeedY = 5;

        public GameTasks() {
            Timer asteroid = new Timer("AsteroidTask");
            asteroid.scheduleAtFixedRate(createAsteroid(), asteroidInterval, defaultDelay);
        }

        //Задача создать астероиды в случайных x позициях
        private TimerTask createAsteroid() {
            return new TimerTask() {
                @Override
                public void run() {
                    asteroidTimeCounter += asteroidInterval;

                    if (asteroidTimeCounter >= 3000) {
                        asteroidSpeedY++;
                        asteroidTimeCounter = 0;
                    }

                    MyPacket packet = MyPacket.create(MyPacket.TYPE_ASTEROID);
                    int x = r.nextInt(760) + 20;
                    int radius = r.nextInt(3);
                    packet.setValue(1, x);
                    packet.setValue(2, 10);
                    packet.setValue(3, 0);
                    packet.setValue(4, asteroidSpeedY);
                    packet.setValue(5, radius);
                    for (int i : oosList.keySet()) {
                        try {
                            oosList.get(i).write(packet.toByteArray());
                            oosList.get(i).flush();
                        } catch (IOException ex) {
                            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            };
        }
    }

    private byte[] extendArray(byte[] oldArray) {
        int oldSize = oldArray.length;
        byte[] newArray = new byte[oldSize * 2];
        System.arraycopy(oldArray, 0, newArray, 0, oldSize);
        return newArray;
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

    public static void main(String args[]) {
        new Server();
    }
}
