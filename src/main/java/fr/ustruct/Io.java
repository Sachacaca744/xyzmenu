package fr.ustruct;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class Io {
    public static void writeVarInt(DataOutput o, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            o.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        o.writeByte(v);
    }

    public static int readVarInt(DataInput in) throws IOException {
        int r = 0, shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            r |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return r;
            shift += 7;
            if (shift > 35) throw new IOException("varint trop long");
        }
    }
}
