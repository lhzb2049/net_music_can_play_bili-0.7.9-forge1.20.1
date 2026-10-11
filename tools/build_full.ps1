<#
    全量构建台账（唯一可信的错误数来源）

    为什么必须存在这个脚本：
      第 61 次发现，Gradle 的 `compileJava` 默认是**增量**的 —— 只重编"受影响"的源文件，
      **没被重编的文件里的错误根本不会被报出来**。于是我把增量数当成了全项目数，
      报出一条"134 → 2（掉了 98.5%）"的曲线，**那条曲线不成立**。
      真实全项目口径当时是 **184**。

    所以：**错误数一律出自本脚本**（它固定跑 `clean compileJava`），
    增量编译的结果**不得当进度**。

    ⚠️ 第 89 轮修掉的一个缺陷（本脚本自己抓不到错）：
      原来这里用 PowerShell 的 `Select-String '\.java:\d+:'` 数"报错文件数"来探测
      javac 提前中止。但那个模式**把警告也算进去** ⇒ 别的文件里有 2 条警告就能把
      "只有 1 个文件报错"的情况伪装成"3 个文件报错"，**截断探测形同虚设**。
      现在所有按中文标签的判断都交给 `tools/gradle_ledger.py`（它按 GBK 解码，
      并且有 8 条负例自检），本脚本只读它输出的 **ASCII 机器行**：

          LEDGER errors=<N> warnings=<M> error_files=<K>

    用法：
        powershell -File tools\build_full.ps1                     # 出全量错误数
        powershell -File tools\build_full.ps1 -Canary <必须报错的文件>   # 截断判成硬失败
        powershell -File tools\build_full.ps1 -SelfTest            # 负例：台账判据 + 不接受外部日志
    退出码：0 = 编译通过（0 错误）；1 = 有错误（读数可用）；2 = 结果不可用（语法类/截断/账本异常）；3 = 前置缺失。
#>
[CmdletBinding()]
param(
    [switch]$SelfTest,
    [string[]]$Canary = @()
)

$ErrorActionPreference = 'Stop'
$root  = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$proj  = Join-Path $root 'ncpb-assembled'
$outDir = Join-Path $root '_port-compile'
$raw    = Join-Path $outDir 'gradle-full.txt'
$py     = 'D:\Python311\python.exe'
$ledger = Join-Path $root 'tools\gradle_ledger.py'

if ($SelfTest) {
    $bad = 0

    # 负例 1：台账自己的判据（含"警告不能抬高错误文件数"这条，就是本轮修的 bug）
    Write-Host '=== [负例] 台账判据（tools/gradle_ledger.py --selftest）==='
    & $py $ledger --selftest
    if ($LASTEXITCODE -ne 0) { Write-Host '!! 台账负例自检失败'; $bad++ } else { Write-Host '台账负例自检：通过' }

    # 负例 2：本脚本**从不**接受外部日志；造一份"增量式"输出，它必须不被采纳
    Write-Host ''
    Write-Host '=== [负例] 造一份只含少量错误、且**不是 clean 构建**的输出 ==='
    $fake = Join-Path $outDir 'gradle-incremental-fake.txt'
    @(
        'D:\x\Foo.java:3: 错误: 找不到符号',
        '    import a.b.C;'
    ) | Set-Content -LiteralPath $fake -Encoding UTF8
    & $py $ledger --raw $fake | Out-Host
    Write-Host ('[负例] ledger 退出码 = ' + $LASTEXITCODE + '（这不是重点）')
    Write-Host '[负例] 重点：本脚本**从不**接受外部日志，它只跑 clean 构建 ——'
    Write-Host '       也就是说，"拿一份增量输出冒充全量"在这条路径上不可能发生。'
    Remove-Item $fake -Force

    # 负例 3：机器行缺失时必须判"不可用"（PowerShell 这一侧的判据）
    Write-Host ''
    Write-Host '=== [负例] 机器行解析：伪造一段没有 LEDGER 行的输出 ==='
    $noLine = "错误 3 条`n警告 0 条"
    $m = [regex]::Match($noLine, 'LEDGER errors=(\d+) warnings=(\d+) error_files=(\d+)')
    if ($m.Success) { Write-Host '!! 没有机器行却被解析成功 —— 判据是死的'; $bad++ }
    else { Write-Host '机器行缺失 ⇒ 按设计判"不可用"（PASS）' }

    if ($bad -gt 0) { Write-Host ('---- 负例自检：失败 %d 项' % $bad); exit 2 }
    Write-Host '---- 负例自检：全部通过'
    exit 0
}

if (-not (Test-Path $proj)) { Write-Host "前置缺失：$proj"; exit 3 }
if (-not (Test-Path $ledger)) { Write-Host "前置缺失：$ledger"; exit 3 }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# 本机路径不写死：JDK 17 由调用方通过 JAVA_HOME 提供；GRADLE_USER_HOME 可选（不设就用默认缓存目录）。
if (-not $env:JAVA_HOME) {
    throw '请先设置 JAVA_HOME 指向 JDK 17（例如 $env:JAVA_HOME = ''C:\path\to\jdk-17''）'
}

Write-Host '=== 全量构建：gradlew clean compileJava ==='
Push-Location $proj
# 用 cmd 重定向按**原始字节**落盘：gradle 在中文 Windows 上输出 GBK，
# 经 PowerShell 管道会把标签变成 '????'（第 32 次就因此读错过）。
cmd /c ".\gradlew.bat clean compileJava --offline --no-daemon --console=plain > `"$raw`" 2>&1"
$gradleExit = $LASTEXITCODE
Pop-Location
Write-Host ("gradle 退出码 = $gradleExit")

if (-not (Test-Path $raw)) { Write-Host '前置缺失：没有产出日志'; exit 3 }

# 台账：所有中文标签判断都在 Python 侧（GBK 正确），这里只取 ASCII 机器行。
$ledgerArgs = @($ledger, '--raw', $raw, '--top', '12')
if ($Canary.Count -gt 0) { $ledgerArgs += @('--canary') + $Canary }
$ledgerText = & $py @ledgerArgs | Out-String
Write-Host $ledgerText
$ledgerExit = $LASTEXITCODE

$m = [regex]::Match($ledgerText, 'LEDGER errors=(\d+) warnings=(\d+) error_files=(\d+)')
if (-not $m.Success) {
    Write-Host ''
    Write-Host '!! 台账没有给出机器行（LEDGER errors=…）⇒ 数字不可用。'
    Write-Host '!! 不允许"读不出来"被当成"全绿"（第 78 次的教训：构建脚本报错时台账曾报 0 错误）。'
    exit 2
}
$ledgerErrors = [int]$m.Groups[1].Value
$ledgerWarns  = [int]$m.Groups[2].Value
$ledgerFiles  = [int]$m.Groups[3].Value

Write-Host ''
Write-Host ("结论：[全量口径] 错误 $ledgerErrors 条 / 警告 $ledgerWarns 条 / 出错文件 $ledgerFiles 个（台账退出码 $ledgerExit）")

# 硬判据 1：构建失败 + **0 条编译错误** ⇒ 失败发生在编译之前（构建脚本报错、依赖缺失、
# 配置阶段崩溃…）。第 78 次因此把"0 错误"当成了全绿 —— 绝不允许。
if ($gradleExit -ne 0 -and $ledgerErrors -eq 0) {
    Write-Host '!! 构建失败，但**没有任何编译错误** ⇒ 失败发生在编译之前。'
    Write-Host '!! （构建脚本报错、依赖缺失、配置阶段崩溃等）'
    Write-Host '!! 因此"错误 0 条"**不可信**，不得当作全绿。'
    Select-String -LiteralPath $raw -Pattern 'What went wrong|error:|BUILD FAILED|Unexpected input' |
        Select-Object -First 5 | ForEach-Object { Write-Host ('   ' + $_.Line.Trim()) }
    exit 2
}

# 硬判据 2：台账说总数不可用（语法类 / canary 缺席）
if ($ledgerExit -eq 2) { exit 2 }

if ($gradleExit -eq 0) { exit 0 }
exit 1
