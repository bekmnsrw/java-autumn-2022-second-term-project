import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
public class MyPacket {
    private static final byte HEADER_1 = (byte)0xe4;
    private static final byte HEADER_2 = (byte)0x15;

    private static final byte FOOTER_1 = (byte)0x00;
    private static final byte FOOTER_2 = (byte)0x90;

    public static final byte TYPE_MOVE = 1;
    public static final byte SUBTYPE_HANDSHAKE = 2;
    public static final byte TYPE_FIRE = 3;

    public static final byte TYPE_OK = 4;
    public static final byte TYPE_ID = 5;
    public static final byte TYPE_START = 6;
    public static final byte TYPE_ASTEROID = 7;

    private byte type;
    //    private byte subtype;
    public static byte[] secretKey;
    private List<AwesomeField> fields = new ArrayList<>();

    private MyPacket() {

    }

    public static boolean compareEOP(byte[] array, int lastItem) {
        return array[lastItem - 1] == FOOTER_1 && array[lastItem] == FOOTER_2;
    }

    public byte[] toByteArray() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(new byte[] { HEADER_1, HEADER_2, type,
//                            subtype
            });

            for (AwesomeField field : fields) {
                out.write(field.id);
                out.write(field.size);
                out.write(field.data);
            }

            out.write(new byte[] { FOOTER_1, FOOTER_2 });

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static MyPacket parse(byte[] data) {
        if (data[0] != HEADER_1 && data[1] != HEADER_2 || data[data.length - 1] != FOOTER_2 && data[data.length - 2] != FOOTER_1) {
            throw new IllegalArgumentException("Unknown packet format");
        }

        byte type = data[2];
        MyPacket packet = MyPacket.create(type);

        int offset = 3;

        while (true) {
            if (data.length - 2 <= offset) {
                return packet;
            }

            byte fieldId = data[offset];
            byte fieldSize = data[offset + 1];
            byte[] content = new byte[Byte.toUnsignedInt(fieldSize)];

            if (fieldSize != 0) {
                System.arraycopy(data, offset + 2, content, 0, Byte.toUnsignedInt(fieldSize));
            }

            AwesomeField field = new AwesomeField(fieldId, fieldSize, content);
            packet.getFields().add(field);

            offset += 2 + fieldSize;
        }
    }

    public AwesomeField getField(int id) {
        Optional<AwesomeField> field = getFields().stream().filter(f -> f.getId() == (byte)id).findFirst();

        if (field.isEmpty()) {
            throw new IllegalArgumentException("No field with " + id + " id");
        }

        return field.get();
    }

    public Object getValue(int id) {
        AwesomeField field = getField(id);
        byte[] data = field.getData();

        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(int id, Class<T> type) {
        return (T)getValue(id);
    }

    public void setValue(int id, Object value) {
        AwesomeField field;

        try {
            field = getField(id);
        } catch (IllegalArgumentException e) {
            field = new AwesomeField((byte)id);
        }

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);

            byte[] data = bos.toByteArray();

            if (data.length > 255) {
                throw new IllegalArgumentException("Too much data sent");
            }

            field.setSize((byte)data.length);
            field.setData(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        getFields().add(field);
    }

    public static MyPacket create(int type) {
        MyPacket packet = new MyPacket();
        packet.type = (byte)type;
        return packet;
    }

    @Data
    @AllArgsConstructor
    private static class AwesomeField {
        private byte id;
        private byte size;
        private byte[] data;

        public AwesomeField(byte id) {
            this.id = id;
        }
    }
}
