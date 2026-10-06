import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;

/** Deterministically creates the empty 8x8x8 GameTest structure using standard NBT. */
class GenerateFixture {
    public static void main(String[] args) throws Exception {
        Path file = Path.of("src/main/resources/data/psychokinesis/structures/empty.nbt");
        Files.createDirectories(file.getParent());
        try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            out.writeByte(10); out.writeUTF("");
            out.writeByte(3); out.writeUTF("DataVersion"); out.writeInt(3465);
            out.writeByte(9); out.writeUTF("size"); out.writeByte(3); out.writeInt(3);
            out.writeInt(8); out.writeInt(8); out.writeInt(8);
            out.writeByte(9); out.writeUTF("palette"); out.writeByte(10); out.writeInt(1);
            out.writeByte(8); out.writeUTF("Name"); out.writeUTF("minecraft:air"); out.writeByte(0);
            for (String key : new String[]{"blocks", "entities"}) {
                out.writeByte(9); out.writeUTF(key); out.writeByte(10); out.writeInt(0);
            }
            out.writeByte(0);
        }
    }
}
