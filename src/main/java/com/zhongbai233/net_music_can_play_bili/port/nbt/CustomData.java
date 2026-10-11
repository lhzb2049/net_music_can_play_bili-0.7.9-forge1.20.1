package com.zhongbai233.net_music_can_play_bili.port.nbt;

import java.util.function.Consumer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 1.21 的 `CustomData` 在 1.20.1 的**垫片**。
 *
 * <p><b>为什么用垫片而不是逐个改写调用点</b>：1.21 用 `DataComponents.CUSTOM_DATA`
 * 把自定义 NBT 包成 `CustomData` 对象；1.20.1 的物品数据**就是** `ItemStack.getTag()`
 * 返回的裸 `CompoundTag`。两者是同一份数据、只是外面多了一层壳。
 * 与其把 5 个大文件（`PadItem` / `MP4Item` / `LinkHelper` / `AudioLinkData` /
 * `HolographicGlassesItem`）里的逻辑逐行改写（风险高、review 成本高），
 * 不如提供一层同 API 的壳，让原逻辑**一行不改**地继续工作。</p>
 *
 * <p><b>存档往返</b>：垫片内部持有的就是 `ItemStack` 的真实 NBT，
 * 所以 `setTag` / `getTag` 写读的是同一份数据 —— 这正是 A5（存档往返等价）需要的。</p>
 *
 * <p><b>故意不实现的方法</b>：`getAsJsonObject()` / `getAsJsonArray()`。
 * 1.20.1 的 `CompoundTag` 没有这两个方法，硬造一个语义不对的实现会**静默**出错；
 * 所以干脆不提供，让编译器把那几处点出来人工处理（约 5 处）。</p>
 */
public final class CustomData {

    /** 对应 1.21 的 `CustomData.EMPTY`。 */
    public static final CustomData EMPTY = new CustomData(new CompoundTag());

    private final CompoundTag tag;

    private CustomData(CompoundTag tag) {
        this.tag = tag;
    }

    // ---------- 构造 ----------

    public static CustomData of(CompoundTag tag) {
        return new CustomData(tag == null ? new CompoundTag() : tag);
    }

    /** `CustomData.from(stack)` 的对应物：没有 NBT 时返回 null（与 1.21 语义一致）。 */
    public static CustomData ofNullable(CompoundTag tag) {
        return tag == null ? null : new CustomData(tag);
    }

    public static CustomData from(ItemStack stack) {
        return ofNullable(stack.getTag());
    }

    // ---------- ItemStack 上的操作（1.21 的 get/set/update/remove 对应物） ----------

    public static void setOn(ItemStack stack, CustomData data) {
        stack.setTag(data == null || data.isEmpty() ? null : data.copyTag());
    }

    /**
     * 对应 1.21 的 `CustomData.updateOn(stack, fn)`。
     *
     * <p>⚠️ 回调吃的是 **`CustomData`**，不是 `CompoundTag` —— 因为原代码里的 lambda 长这样：
     * `customData -> customData.update(tag -> …)`，它调的是 `CustomData.update`。
     * 第一版把参数写成了 `CompoundTag`，于是 `customData.update(...)` 找不到符号
     * （编译器当场报出来，见 PORT-STATUS §3.27）。</p>
     */
    public static void updateOn(ItemStack stack, Consumer<CustomData> action) {
        CompoundTag t = stack.getOrCreateTag();
        action.accept(new CustomData(t));
        if (t.isEmpty()) {
            stack.setTag(null);
        }
    }

    public static void removeFrom(ItemStack stack) {
        stack.setTag(null);
    }

    // ---------- CustomData 自身的 API ----------

    public CompoundTag copyTag() {
        return this.tag.copy();
    }

    public boolean isEmpty() {
        return this.tag.isEmpty();
    }

    public boolean has(String key) {
        return this.tag.contains(key);
    }

    /** 返回 `Tag`（可能是 null），与 1.21 的 `CustomData.get(String)` 一致。 */
    public Tag get(String key) {
        return this.tag.get(key);
    }

    public CustomData copy() {
        return new CustomData(this.tag.copy());
    }

    /** 就地对底层 NBT 做修改（对应 1.21 的 `CustomData.update(Consumer)`）。 */
    public void update(Consumer<CompoundTag> action) {
        action.accept(this.tag);
    }

    @Override
    public String toString() {
        return "CustomData" + this.tag;
    }
}
