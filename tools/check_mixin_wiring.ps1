<#
    Mixin 接线门禁（阶段 1 的关键验收）

    为什么需要它：1.20.1 上 Mixin 的运行期目标是 **SRG 名**，补丁要生效必须靠 Annotation Processor
    生成的 refmap 把官方名映射过去。漏掉这条链路的症状是**补丁静默不生效**——没有异常、没有日志，
    游戏看起来"只是没效果"（HANDOVER §3.1 第 3 点、misakanet-pending/L4）。

    五条断言（每条都把判据打出来）：
      1. jar 里存在 <modid>.mixins.json
      2. MANIFEST.MF 里有 MixinConfigs 且指向该配置（1.20.1 Forge 的发现方式；NeoForge 才用 toml）
      3. mixins.json 的 compatibilityLevel 是 JAVA_17，且有 refmap 键
      4. jar 里存在该 refmap 文件
      5. **refmap 非空**（mappings 至少 1 条）—— 这条才是"AP 真的跑了"的证据

    自检 `-SelfTest`：对 netmusic 的生产 jar（有 mixins 吗？没有）跑同一套检查，**必须失败**。
    这条负例证明检查有鉴别力（而不是永远 PASS）。

    用法：
      powershell.exe -NoProfile -ExecutionPolicy Bypass -File check_mixin_wiring.ps1
      powershell.exe -NoProfile -ExecutionPolicy Bypass -File check_mixin_wiring.ps1 -SelfTest

    退出码：0 = 通过；2 = 有断言失败；3 = 前置缺失。
#>
[CmdletBinding()]
param(
    # 默认查**当前交付件**所在的工程。历史：这个默认值曾长期是 `ncpb-1.20.1`（外观层，
    # 单探针 Mixin、127 KB 的 jar），而交付 jar 自第 88 次起由 `ncpb-assembled` 产出
    # ⇒ 门禁一直在查没人部署的那个 jar（PORT-STATUS §6.24）。
    [string]$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$Jar,
    [string]$ModId = 'net_music_can_play_bili',
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not $Jar) {
    $libs = Join-Path $ProjectDir 'build\libs'
    $jars = @(Get-ChildItem $libs -Filter *.jar -ErrorAction SilentlyContinue)
    if ($jars.Count -eq 0) { throw "build/libs 下没有 jar（先 gradlew build）" }
    $Jar = $jars[0].FullName
}

# 自检要的是**真的负例**：一个不含 Mixin 接线的 jar。
# netmusic 的生产 jar 正好是这种（它有 AT，但没有 mixins.json / refmap / MixinConfigs）。
if ($SelfTest -and -not $PSBoundParameters.ContainsKey('Jar')) {
    $neg = Join-Path (Split-Path $ProjectDir -Parent) 'libs\netmusic-1.5.2-forge+mc1.20.1.jar'
    if (-not (Test-Path $neg)) { throw "自检需要负例 jar（不含 Mixin），但不存在：$neg" }
    $Jar = $neg
}

function Read-ZipEntry {
    param([string]$ZipPath, [string]$EntryName)
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
    try {
        $e = $zip.Entries | Where-Object { $_.FullName -eq $EntryName } | Select-Object -First 1
        if (-not $e) { return $null }
        $sr = New-Object System.IO.StreamReader($e.Open())
        try { return $sr.ReadToEnd() } finally { $sr.Dispose() }
    } finally { $zip.Dispose() }
}

function Get-ZipEntries {
    param([string]$ZipPath)
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
    try { return @($zip.Entries | ForEach-Object { $_.FullName }) } finally { $zip.Dispose() }
}

$results = New-Object System.Collections.Generic.List[object]
function Assert-It {
    param([string]$Label, [bool]$Ok, [string]$Evidence)
    Write-Host ("  [{0}] {1}" -f $(if ($Ok) { 'PASS' } else { 'FAIL' }), $Label)
    Write-Host ("        {0}" -f $Evidence)
    $results.Add([pscustomobject]@{ Ok = $Ok; Label = $Label })
}

$mixinsJson = "$ModId.mixins.json"
$refmapName = "$ModId.refmap.json"

Write-Host "=== Mixin 接线门禁 ==="
Write-Host "待检 jar：$Jar"
if ($SelfTest) { Write-Host "自检模式：对**不含 Mixin** 的 jar 跑同一套检查，必须失败。`n" } else { Write-Host "" }

$entries = Get-ZipEntries -ZipPath $Jar

# 1) mixin 配置在 jar 里
Assert-It -Label "jar 内含 $mixinsJson" -Ok ($entries -contains $mixinsJson) `
    -Evidence ("jar 条目数 {0}；命中={1}" -f $entries.Count, ($entries -contains $mixinsJson))

# 2) manifest 的 MixinConfigs
$manifest = Read-ZipEntry -ZipPath $Jar -EntryName 'META-INF/MANIFEST.MF'
$manifestText = if ($manifest) { $manifest -replace "`r`n", "`n" } else { '' }
$mixinConfigsOk = ($manifestText -match "(?m)^MixinConfigs:.*$mixinsJson")
Assert-It -Label "MANIFEST.MF 的 MixinConfigs 指向该配置" -Ok $mixinConfigsOk `
    -Evidence ($(if ($manifestText -match '(?m)^MixinConfigs:.*$') { 'MixinConfigs = ' + ($Matches[0] -replace '^MixinConfigs:\s*', '') } else { '（MANIFEST 里没有 MixinConfigs 行）' }))

# 3) mixins.json 的内容
$mj = Read-ZipEntry -ZipPath $Jar -EntryName $mixinsJson
if ($mj) {
    $o = $mj | ConvertFrom-Json
    $levelOk = ($o.compatibilityLevel -eq 'JAVA_17')
    $refmapOk = ($o.refmap -eq $refmapName)
    Assert-It -Label 'mixins.json 的 compatibilityLevel = JAVA_17' -Ok $levelOk -Evidence ("实际=" + $o.compatibilityLevel)
    Assert-It -Label "mixins.json 的 refmap = $refmapName" -Ok $refmapOk -Evidence ("实际=" + $o.refmap)
} else {
    Assert-It -Label 'mixins.json 可读取' -Ok $false -Evidence '（读不到该条目）'
}

# 4) refmap 文件存在
Assert-It -Label "jar 内含 $refmapName" -Ok ($entries -contains $refmapName) -Evidence ("命中={0}" -f ($entries -contains $refmapName))

# 5) refmap 非空（AP 真的跑了的证据）
$refmapText = Read-ZipEntry -ZipPath $Jar -EntryName $refmapName
if ($refmapText) {
    $r = $refmapText | ConvertFrom-Json
    $count = 0
    if ($r.mappings) { $count = ($r.mappings.PSObject.Properties | Measure-Object).Count }
    Assert-It -Label 'refmap 非空（mappings 至少 1 条）' -Ok ($count -gt 0) `
        -Evidence ("mappings 条目数={0}；mappingTypes={1}" -f $count, ($r.mappingTypes -join ','))
    # 断言“AP 真的把官方名映射成了 SRG 名”。两档判据，命中任一即算过：
    #   ① 阶段 0 探针 Mixin 的痕迹（`ncpb-1.20.1` 那套）；
    #   ② 交付工程里**任意一条真实 SRG 名映射** `m_<数字>_`。
    # 第 92 次补 ② 的原因：`ncpb-assembled`（真正的交付件）没有探针 Mixin，
    # 只认探针关键词会让这条断言在交付件上**永远 FAIL** —— 一条永远失败的断言等于没查。
    $probeHit = ($refmapText -match 'getHoverName|m_280578_|ncpb\$refmapProbe|P0RefmapProbeMixin')
    $srgHit   = ($refmapText -match 'm_\d+_')
    $probeOk  = ($probeHit -or $srgHit)
    Assert-It -Label 'refmap 里有官方名→SRG 名的映射痕迹' -Ok $probeOk `
        -Evidence ("探针关键词={0}；真实 SRG 映射 m_<n>_={1}" -f $probeHit, $srgHit)
} else {
    Assert-It -Label 'refmap 可读取' -Ok $false -Evidence '（读不到该条目）'
}

$failed = @($results | Where-Object { -not $_.Ok })
Write-Host ""
if ($SelfTest) {
    if ($failed.Count -gt 0) {
        Write-Host ("自检通过：负例 jar 造成 {0} 条 FAIL（{1}），说明这些断言确有鉴别力。" -f $failed.Count, (($failed | ForEach-Object { $_.Label }) -join '；'))
        exit 0
    }
    Write-Host "自检失败：负例 jar 竟然全部 PASS —— 门禁没有鉴别力。"
    exit 2
}

if ($failed.Count -eq 0) {
    Write-Host "结论：通过 —— Mixin 配置可见、refmap 已生成且非空，1.20.1 上的补丁链路是活的。"
    exit 0
}
Write-Host ("结论：失败 —— {0} 条断言不成立：{1}" -f $failed.Count, (($failed | ForEach-Object { $_.Label }) -join '；'))
exit 2
