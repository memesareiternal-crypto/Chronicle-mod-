package dev.chronicle.test;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.chronicle.Settings;
import net.minecraftforge.common.ForgeConfigSpec;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Isolates test overrides from file watchers and restores Forge's value caches on exit. */
public final class TestConfigScope implements AutoCloseable {
    private final CommentedConfig original;
    private boolean closed;

    public TestConfigScope() {
        original = currentConfig();
        Settings.SPEC.setConfig(copyConfig(original));
    }

    private static CommentedConfig currentConfig() {
        try {
            Field field = ForgeConfigSpec.class.getDeclaredField("childConfig");
            field.setAccessible(true);
            Object value = field.get(Settings.SPEC);
            if (value instanceof CommentedConfig config) return config;
            throw new IllegalStateException("Forge test config has not been loaded as a commented config");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to isolate Forge test configuration", exception);
        }
    }

    private static CommentedConfig copyConfig(UnmodifiableConfig source) {
        CommentedConfig copy = CommentedConfig.inMemory();
        for (var entry : source.entrySet()) {
            List<String> path = List.of(entry.getKey());
            copy.set(path, copyValue(entry.getValue()));
            if (source instanceof UnmodifiableCommentedConfig commented) {
                String comment = commented.getComment(path);
                if (comment != null) copy.setComment(path, comment);
            }
        }
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof UnmodifiableConfig config) return copyConfig(config);
        if (value instanceof List<?> list) {
            var copy = new ArrayList<Object>(list.size());
            for (Object element : list) copy.add(copyValue(element));
            return copy;
        }
        return value;
    }

    @Override public void close() {
        if (!closed) {
            closed = true;
            Settings.SPEC.setConfig(original);
        }
    }
}
