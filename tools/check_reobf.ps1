<#
    重混淆门禁（阶段 0 的核心验收之一）

    为什么它必须在阶段 0 就存在：1.20.1 的产物必须重混淆回 SRG 才能跑。
    漏掉它的表现是"dev 环境一切正常，进游戏 NoSuchMethodError / Mixin 静默失效"——
    属于最难查的一类（HANDOVER §7.3）。所以这里把"成品里必须是 SRG 名、dev 里必须是官方名"
    做成一条会说"不"的断言，而不是等人进游戏看。

    被检对象（第 105 次起）：**随发布包一起发出去的**类
    `blockentity/BlockEntityRenderBounds` 里调用了 `BlockPos.getX()/getY()/getZ()`
      * dev 产物（build/classes/java/main）        → 必须含 `getX`，且不含 `m_123341_`
      * 重混淆产物（build/reobfJar/output.jar）    → 必须含 `m_123341_`，且不含 `getX`
      * 可部署产物（build/libs/*.jar）             → 同上（这才是会被丢进 mods/ 的东西）

    为什么从"专用探针类"改成"随包发布的类"：`P0ReobfProbe` 是构建期自检类，
    发布卫生要求它**不进 jar**（build.gradle 里已排除）⇒ 用它当被检对象会让
    "reobf / build/libs" 两条断言失去对象。换成真实发出去的类，
    证据完全等价（同样是"调用了 vanilla 成员、看它在哪一层变成了 SRG 名"），而且更强：
    它同时证明了**发布包本体**确实被重混淆过。

    自检：`-SelfTest` 会把 dev 产物塞进"重混淆产物"的位置。**门禁必须因此失败**——
    如果它照样报 PASS，说明这条断言没有鉴别力（L2/L12 的教训：检查自己也要被检查）。

    用法：
      powershell.exe -NoProfile -ExecutionPolicy Bypass -File check_reobf.ps1
      powershell.exe -NoProfile -ExecutionPolicy Bypass -File check_reobf.ps1 -SelfTest

    退出码：0 = 通过；2 = 断言失败；3 = 前置缺失（产物还没生成）。
#>
[CmdletBinding()]
param(
    # 默认查**当前交付件**所在的工程。历史：这个默认值曾长期是 `ncpb-1.20.1`（外观层，
    # 127 KB 的 jar），而交付 jar 自第 88 次起由 `ncpb-assembled` 产出 ⇒ 门禁一直在查
    # 没人部署的那个 jar，“交付件有没有重混淆”实际无人检查（PORT-STATUS §6.24）。
    # 两个工程都带同名同签名的探针类，所以换默认值不需要任何其它改动。
    [string]$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$Javap      = $(if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\javap.exe' } else { 'javap' }),
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'

$probe      = 'com.zhongbai233.net_music_can_play_bili.blockentity.BlockEntityRenderBounds'
$official   = 'getX'
$srg        = 'm_123341_'

$devDir   = Join-Path $ProjectDir 'build\classes\java\main'
$reobfJar = Join-Path $ProjectDir 'build\reobfJar\output.jar'

foreach ($p in @($Javap)) { if (-not (Test-Path $p)) { throw "缺少 javap：$p" } }
foreach ($p in @($devDir)) { if (-not (Test-Path $p)) { throw "缺少 dev 产物：$p（先跑 gradlew build）" } }
if (-not $SelfTest -and -not (Test-Path $reobfJar)) { throw "缺少重混淆产物：$reobfJar（先跑 gradlew build）" }

# 被检类缺失 ⇒ 这是**前置缺失（exit 3）**，不是“通过”、也不是“断言失败”。
# 没有这一条时，指向错的工程会以 javap 报错 + 未登记退出码 1 收场（第 92 次实测），
# 而“门禁查不到被检类”和“重混淆坏了”是两件完全不同的事，必须分开说。
if (-not (Test-Path (Join-Path $devDir 'com\zhongbai233\net_music_can_play_bili\blockentity\BlockEntityRenderBounds.class'))) {
    Write-Host "前置缺失：$ProjectDir 的 dev 产物里没有被检类 $probe"
    Write-Host '         （该工程没有这个类 ⇒ 本门禁无法对它下结论；请用 -ProjectDir 指向本工程的组装树，'
    Write-Host '           或确认 blockentity/BlockEntityRenderBounds 是否被改名/移走 —— 见 PORT-STATUS §6.37）'
    exit 3
}

function Get-Disasm {
    param([string]$ClassPath, [string]$Class)
    $out = & $Javap -c -p -classpath $ClassPath $Class 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $out) { throw "javap 失败：$Class @ $ClassPath" }
    return ($out -join "`n")
}

function Test-Mapping {
    param([string]$ClassPath, [string]$Class, [string]$Want, [string]$NotWant, [string]$Label)
    $dis = Get-Disasm -ClassPath $ClassPath -Class $Class
    $hasWant    = $dis.Contains($Want)
    $hasNotWant = $dis.Contains($NotWant)
    $ok = $hasWant -and (-not $hasNotWant)
    $verdict = if ($ok) { 'PASS' } else { 'FAIL' }
    # 注意：报告必须走 Write-Host。写成裸字符串会被算进函数返回值，
    # 调用方拿到的是数组（恒为真）→ 断言失去鉴别力（自检抓到的真实 bug）。
    Write-Host ("  [{0}] {1}" -f $verdict, $Label)
    Write-Host ("        期望含 `"{0}`"：{1}；期望不含 `"{2}`"：{3}" -f $Want, $hasWant, $NotWant, (-not $hasNotWant))
    return [bool]$ok
}

"=== 重混淆门禁 ==="
"探针类：$probe`n"

if ($SelfTest) {
    '自检模式：把 **dev 产物** 放到「重混淆产物」的位置 —— 门禁**必须**失败。'
    $falsePass = Test-Mapping -ClassPath $devDir -Class $probe -Want $srg -NotWant $official `
        -Label '（故意错位）dev 产物被当作重混淆产物检查'
    if ($falsePass) {
        "自检失败：错位的检查竟然报了 PASS —— 这条门禁没有鉴别力。"
        exit 2
    }
    "自检通过：错位时门禁如实报 FAIL，说明它确实在区分官方名与 SRG 名。"
    exit 0
}

$okDev   = Test-Mapping -ClassPath $devDir   -Class $probe -Want $official -NotWant $srg      -Label 'dev 产物应为官方名'
$okReobf = Test-Mapping -ClassPath $reobfJar -Class $probe -Want $srg      -NotWant $official -Label '重混淆产物应为 SRG 名'

# 真正会被人丢进 mods/ 的是 build/libs/*.jar。
# 实测（FG 6.0.54 + Gradle 8.8）：reobfJar 会把 jar 任务产物一并重混淆，
# build/libs/*.jar 与 build/reobfJar/output.jar 内容一致、都是 SRG 名 ——
# 但这是"实测结论"，所以它必须进门外，而不是靠记忆。
$libsDir = Join-Path $ProjectDir 'build\libs'
$libsJars = @(Get-ChildItem $libsDir -Filter *.jar -ErrorAction SilentlyContinue)
if ($libsJars.Count -eq 0) {
    Write-Host "  [WARN] build/libs 下没有 jar（还没打包？）"
}
$okLibs = $true
foreach ($j in $libsJars) {
    if (-not (Test-Mapping -ClassPath $j.FullName -Class $probe -Want $srg -NotWant $official `
                -Label ("可部署产物（build/libs）应为 SRG 名：" + $j.Name))) {
        $okLibs = $false
    }
}

""
if ($okDev -and $okReobf -and $okLibs) {
    "结论：通过 —— dev=官方名、reobf=SRG 名、可部署 jar 也是 SRG 名（$($libsJars.Count) 个），重混淆链路是活的。"
    exit 0
}
"结论：失败 —— 产物名字空间不对（漏了重混淆，或检查本身写错了）。"
exit 2
