package okhttp3.internal;

import java.io.Closeable;

/**
 * 为旧依赖字节码保留的 OkHttp 4 内部方法兼容入口。
 *
 * <p>最初为 ZoomImage 1.6.0 的 Coil 2 适配器引入，补齐 OkHttp 5 已移动的静态方法。
 * 当前应用已使用 Coil 3；此处记录兼容层的历史来源，不表示当前仍使用 Coil 2。</p>
 */
public final class Util {
    private Util() {
    }

    public static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception ignored) {
            // 与 OkHttp 4 的 closeQuietly 语义保持一致。
        }
    }
}
