package okhttp3.internal;

import java.io.Closeable;

/**
 * ZoomImage 1.6.0 与 OkHttp 5 的二进制兼容层。
 *
 * <p>ZoomImage 的 Coil 2 适配器仍调用 OkHttp 4 的内部静态方法。OkHttp 5 已移动该方法，
 * 因此在 ZoomImage 停止依赖此内部 API 前，在应用侧保留等价入口。</p>
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
