import brut.androlib.smali.SmaliBuilder;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import java.io.File;

public class Asm {
    public static void main(String[] a) throws Exception {
        int api = 30;
        DexBuilder db = new DexBuilder(new Opcodes(api));
        if (!new SmaliBuilder(api).buildFile(new File(a[0]), db)) throw new RuntimeException("assemble failed");
        db.writeTo(new FileDataStore(new File(a[1])));
    }
}
