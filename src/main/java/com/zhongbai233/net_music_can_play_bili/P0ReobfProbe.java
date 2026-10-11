package com.zhongbai233.net_music_can_play_bili;

import net.minecraft.core.BlockPos;

/**
 * 重混淆探针（不是产品代码，不要删）。
 *
 * <p>它存在的唯一理由是让“产物必须重混淆回 SRG”这条门禁能被真的检查：
 * <ul>
 *   <li>dev 产物 {@code build/classes/java/main} 里必须是<b>官方名</b> {@code getX}；</li>
 *   <li>重混淆产物 {@code build/reobfJar/output.jar} 与可部署的 {@code build/libs/*.jar}
 *       里必须是 <b>SRG 名</b> {@code m_123341_}。</li>
 * </ul>
 * 漏掉重混淆的表现是“dev 环境一切正常、进游戏 NoSuchMethodError / Mixin 静默失效”，
 * 属于最难查的一类（HANDOVER §7.3）。
 *
 * <p><b>为什么这个类在 ncpb-assembled 里也有一份</b>（PORT-STATUS §6.24）：
 * 交付 jar 现在由 `ncpb-assembled` 产出，而 `tools/check_reobf.ps1` 的探针规格是
 * “dev 官方名 / reobf SRG 名”。没有这个类时，门禁只能去查 `ncpb-1.20.1` 那个
 * **127 KB 的外观层 jar**，于是“交付件有没有重混淆”实际上无人检查。
 * 这里保持与阶段 0 那份**同名同签名**，门禁无需任何特例。
 * 它不被任何代码调用，不进注册表、不进事件总线，对运行时零影响。
 */
public final class P0ReobfProbe {

    private P0ReobfProbe() {
    }

    /** 调用一个官方名的 MC 方法；重混淆后这里的引用应变成 SRG 名。 */
    public static int probeX(BlockPos pos) {
        return pos.getX();
    }
}
