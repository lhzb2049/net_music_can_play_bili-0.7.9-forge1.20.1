package com.zhongbai233.net_music_can_play_bili.port.bridge;

import java.lang.reflect.Field;

import net.minecraft.core.BlockPos;

/**
 * 读 netmusic 1.20.1 `MusicToClientMessage` 私有字段的桥。
 *
 * <p><b>为什么需要它</b>：1.21.1 的 netmusic 给这个类提供了
 * `pos()` / `url()` / `rawUrl()` / `songName()` / `timeSecond()` 五个访问器，
 * 本模组的 `MusicToClientMessageClientMixin` 就是按访问器写的；
 * 而 **1.20.1 的 netmusic 没有任何访问器，字段全是 `private final`**（`javap -p` 实测）。
 * 于是那些调用在 1.20.1 上根本编译不过。</p>
 *
 * <p><b>为什么不用 AccessTransformer</b>：AT 的作用对象是 Minecraft 的类；
 * 对第三方 mod 依赖 jar，ForgeGradle 不会在编译期把 AT 应用到依赖上，
 * javac 看到的仍然是 `private`，编译错误照旧。</p>
 *
 * <p><b>为什么用反射而不是 Mixin 访问器</b>：反射零配置、当场可编译可验证；
 * Mixin 访问器要在 mixin 配置里登记、还要保证运行期真的应用得上。
 * 这条路留作后续优化（见 PORT-STATUS §3.19），当前先保证"读得到、能编译"。
 * 反射只在这 6 个调用点上，且字段查找是 `static` 一次性缓存，热路径开销可忽略。</p>
 */
public final class NetmusicMessageAccess {

    private static final Field POS = field("pos");
    private static final Field URL = field("url");
    private static final Field RAW_URL = field("rawUrl");
    private static final Field SONG_NAME = field("songName");
    private static final Field TIME_SECOND = field("timeSecond");

    /**
     * `SetMusicIDMessage.song` —— 同样是 `private final`、**且没有访问器**（`javap -p` 实测）。
     *
     * <p>第 91 次补：`SetMusicIDMessageMixin` 原来写的是 `message.song()`
     * （1.21.1 的 netmusic 有访问器），1.20.1 上编不过；而它又列在
     * `mixins.json`（`required: true`）里 ⇒ 那个类一旦不在 jar 里，**启动就崩**。</p>
     */
    private static final Field SET_MUSIC_ID_SONG =
            fieldOf("com.github.tartaricacid.netmusic.network.message.SetMusicIDMessage", "song");

    private NetmusicMessageAccess() {
    }

    private static Field fieldOf(String className, String name) {
        try {
            Field f = Class.forName(className).getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("netmusic " + className + " 缺少字段 " + name
                    + "（依赖版本变了？）", e);
        }
    }

    private static Field field(String name) {
        return fieldOf("com.github.tartaricacid.netmusic.network.message.MusicToClientMessage", name);
    }

    private static Object get(Field f, Object message) {
        try {
            return f.get(message);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("读取 netmusic 消息字段失败: " + f.getName(), e);
        }
    }

    public static BlockPos pos(Object message) {
        return (BlockPos) get(POS, message);
    }

    public static String url(Object message) {
        return (String) get(URL, message);
    }

    public static String rawUrl(Object message) {
        return (String) get(RAW_URL, message);
    }

    public static String songName(Object message) {
        return (String) get(SONG_NAME, message);
    }

    public static int timeSecond(Object message) {
        return (Integer) get(TIME_SECOND, message);
    }

    /** `SetMusicIDMessage` 的 `song` 字段（1.20.1 的 netmusic 没有 `song()` 访问器）。 */
    public static Object setMusicIdSong(Object message) {
        return get(SET_MUSIC_ID_SONG, message);
    }
}
