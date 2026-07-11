[CmdletBinding()]
param(
    [string]$BaseUrl = $(if ($env:CA_TEST_BASE_URL) { $env:CA_TEST_BASE_URL } else { "http://127.0.0.1:8080" }),
    [string]$AdminStudentNo = $env:CA_TEST_ADMIN_STUDENT_NO,
    [string]$AdminPassword = $env:CA_TEST_ADMIN_PASSWORD,
    [switch]$SkipRestore
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AdminStudentNo) -or [string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw "请通过 -AdminStudentNo/-AdminPassword 或 CA_TEST_ADMIN_STUDENT_NO/CA_TEST_ADMIN_PASSWORD 提供管理员账号。"
}

$BaseUrl = $BaseUrl.TrimEnd("/")
$script:AdminToken = $null
$script:Results = New-Object System.Collections.Generic.List[object]
$script:CreatedBackups = New-Object System.Collections.Generic.List[string]
$script:TempFiles = New-Object System.Collections.Generic.List[string]

function Add-Result {
    param(
        [string]$Name,
        [string]$Detail = ""
    )
    $script:Results.Add([pscustomobject]@{ name = $Name; status = "OK"; detail = $Detail }) | Out-Null
    if ($Detail) {
        Write-Host ("[OK] {0} - {1}" -f $Name, $Detail)
    } else {
        Write-Host ("[OK] {0}" -f $Name)
    }
}

function ConvertTo-JsonBody {
    param($Body)
    if ($null -eq $Body) { return $null }
    return ($Body | ConvertTo-Json -Depth 30)
}

function Read-ErrorBody {
    param($Exception)
    try {
        $reader = [System.IO.StreamReader]::new($Exception.Response.GetResponseStream())
        return $reader.ReadToEnd()
    } catch {
        return $Exception.Message
    }
}

function Invoke-Json {
    param(
        [ValidateSet("GET", "POST", "PUT", "DELETE")]
        [string]$Method,
        [string]$Path,
        $Body = $null,
        [string]$Token = $script:AdminToken,
        [int[]]$ExpectedStatus = @(200)
    )
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $headers
        ErrorAction = "Stop"
    }
    $json = ConvertTo-JsonBody $Body
    if ($null -ne $json) {
        $params["ContentType"] = "application/json; charset=utf-8"
        $params["Body"] = $json
    }
    try {
        $response = Invoke-WebRequest @params
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        if ($ExpectedStatus -contains $status) {
            return $null
        }
        $bodyText = Read-ErrorBody $_.Exception
        throw "$Method $Path 返回 $status：$bodyText"
    }
    if (-not ($ExpectedStatus -contains [int]$response.StatusCode)) {
        throw "$Method $Path 期望状态 $($ExpectedStatus -join ',')，实际 $($response.StatusCode)：$($response.Content)"
    }
    if ([string]::IsNullOrWhiteSpace($response.Content)) {
        return $null
    }
    $contentType = [string]$response.Headers["Content-Type"]
    if ($contentType -like "*application/json*") {
        return ($response.Content | ConvertFrom-Json)
    }
    return $response.Content
}

function Invoke-Download {
    param(
        [string]$Path,
        [string]$OutFile,
        [string]$Token = $script:AdminToken,
        [int[]]$ExpectedStatus = @(200)
    )
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl$Path" -Headers $headers -Method GET -OutFile $OutFile -PassThru -ErrorAction Stop
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        if ($ExpectedStatus -contains $status) {
            return $null
        }
        $bodyText = Read-ErrorBody $_.Exception
        throw "GET $Path 下载返回 $status：$bodyText"
    }
    if (-not ($ExpectedStatus -contains [int]$response.StatusCode)) {
        throw "GET $Path 下载期望状态 $($ExpectedStatus -join ',')，实际 $($response.StatusCode)"
    }
    return Get-Item -LiteralPath $OutFile
}

function Invoke-JsonDownload {
    param(
        [string]$Path,
        [string]$OutFile,
        $Body,
        [string]$Token = $script:AdminToken,
        [int[]]$ExpectedStatus = @(200)
    )
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{
        Uri = "$BaseUrl$Path"
        Method = "POST"
        Headers = $headers
        ContentType = "application/json; charset=utf-8"
        Body = (ConvertTo-JsonBody $Body)
        OutFile = $OutFile
        PassThru = $true
        ErrorAction = "Stop"
    }
    try {
        $response = Invoke-WebRequest @params
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        if ($ExpectedStatus -contains $status) { return $null }
        $bodyText = Read-ErrorBody $_.Exception
        throw "POST $Path 下载返回 $status：$bodyText"
    }
    if (-not ($ExpectedStatus -contains [int]$response.StatusCode)) {
        throw "POST $Path 下载期望状态 $($ExpectedStatus -join ',')，实际 $($response.StatusCode)"
    }
    return Get-Item -LiteralPath $OutFile
}

function Invoke-Upload {
    param(
        [string]$Path,
        [string]$FilePath,
        [string]$Token = $script:AdminToken
    )
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    try {
        return Invoke-RestMethod -Uri "$BaseUrl$Path" -Method POST -Headers $headers -Form @{ file = Get-Item -LiteralPath $FilePath } -ErrorAction Stop
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        $bodyText = Read-ErrorBody $_.Exception
        throw "POST $Path 上传返回 $status：$bodyText"
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Assert-ZipEntry {
    param([string]$Path, [string]$EntryName)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        Assert-True ([bool]($zip.Entries | Where-Object { $_.FullName -eq $EntryName })) "压缩文件缺少 $EntryName：$Path"
    } finally {
        $zip.Dispose()
    }
}

function Assert-Xlsx {
    param([string]$Path)
    Assert-True ((Get-Item -LiteralPath $Path).Length -gt 1000) "Excel 文件过小：$Path"
    Assert-ZipEntry -Path $Path -EntryName "xl/workbook.xml"
}

function Assert-ZipBackup {
    param([string]$Path)
    Assert-True ((Get-Item -LiteralPath $Path).Length -gt 1000) "备份文件过小：$Path"
    Assert-ZipEntry -Path $Path -EntryName "metadata.json"
}

function ConvertTo-ExcelColumnName {
    param([int]$Index)
    $name = ""
    $number = $Index
    do {
        $remainder = $number % 26
        $name = [char](65 + $remainder) + $name
        $number = [math]::Floor($number / 26) - 1
    } while ($number -ge 0)
    return $name
}

function ConvertTo-InlineCellXml {
    param([string]$Value, [string]$Ref)
    $escaped = [System.Security.SecurityElement]::Escape([string]$Value)
    return "<c r=`"$Ref`" t=`"inlineStr`"><is><t>$escaped</t></is></c>"
}

function New-SimpleXlsx {
    param(
        [string]$Path,
        [object[][]]$Rows
    )
    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew)
    try {
        $zip = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            function Add-Entry([string]$Name, [string]$Text) {
                $entry = $zip.CreateEntry($Name)
                $writer = [System.IO.StreamWriter]::new($entry.Open(), [System.Text.UTF8Encoding]::new($false))
                try { $writer.Write($Text) } finally { $writer.Dispose() }
            }

            $rowXml = New-Object System.Text.StringBuilder
            for ($r = 0; $r -lt $Rows.Count; $r++) {
                [void]$rowXml.Append("<row r=`"$($r + 1)`">")
                for ($c = 0; $c -lt $Rows[$r].Count; $c++) {
                    $ref = "$(ConvertTo-ExcelColumnName $c)$($r + 1)"
                    [void]$rowXml.Append((ConvertTo-InlineCellXml -Value ([string]$Rows[$r][$c]) -Ref $ref))
                }
                [void]$rowXml.Append("</row>")
            }

            Add-Entry "[Content_Types].xml" "<?xml version=`"1.0`" encoding=`"UTF-8`"?><Types xmlns=`"http://schemas.openxmlformats.org/package/2006/content-types`"><Default Extension=`"rels`" ContentType=`"application/vnd.openxmlformats-package.relationships+xml`"/><Default Extension=`"xml`" ContentType=`"application/xml`"/><Override PartName=`"/xl/workbook.xml`" ContentType=`"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml`"/><Override PartName=`"/xl/worksheets/sheet1.xml`" ContentType=`"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml`"/></Types>"
            Add-Entry "_rels/.rels" "<?xml version=`"1.0`" encoding=`"UTF-8`"?><Relationships xmlns=`"http://schemas.openxmlformats.org/package/2006/relationships`"><Relationship Id=`"rId1`" Type=`"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument`" Target=`"xl/workbook.xml`"/></Relationships>"
            Add-Entry "xl/workbook.xml" "<?xml version=`"1.0`" encoding=`"UTF-8`"?><workbook xmlns=`"http://schemas.openxmlformats.org/spreadsheetml/2006/main`" xmlns:r=`"http://schemas.openxmlformats.org/officeDocument/2006/relationships`"><sheets><sheet name=`"Sheet1`" sheetId=`"1`" r:id=`"rId1`"/></sheets></workbook>"
            Add-Entry "xl/_rels/workbook.xml.rels" "<?xml version=`"1.0`" encoding=`"UTF-8`"?><Relationships xmlns=`"http://schemas.openxmlformats.org/package/2006/relationships`"><Relationship Id=`"rId1`" Type=`"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet`" Target=`"worksheets/sheet1.xml`"/></Relationships>"
            Add-Entry "xl/worksheets/sheet1.xml" "<?xml version=`"1.0`" encoding=`"UTF-8`"?><worksheet xmlns=`"http://schemas.openxmlformats.org/spreadsheetml/2006/main`"><sheetData>$rowXml</sheetData></worksheet>"
        } finally {
            $zip.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
    $script:TempFiles.Add($Path) | Out-Null
    return $Path
}

function New-TempPath {
    param([string]$Name)
    $path = Join-Path $env:TEMP ("ca-smoke-{0}-{1}" -f ([Guid]::NewGuid().ToString("N").Substring(0, 8)), $Name)
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    $script:TempFiles.Add($path) | Out-Null
    return $path
}

function Get-DefaultPassword {
    param([string]$StudentNo)
    if ($StudentNo.Length -le 6) { return $StudentNo }
    return $StudentNo.Substring($StudentNo.Length - 6)
}

function Get-EnabledWeekdayDate {
    param([int]$Weekday)
    $today = Get-Date
    $current = [int]$today.DayOfWeek
    if ($current -eq 0) { $current = 7 }
    $delta = $Weekday - $current
    return $today.Date.AddDays($delta)
}

function Remember-Backup {
    param($BackupItem)
    if ($BackupItem -and $BackupItem.filename) {
        $script:CreatedBackups.Add([string]$BackupItem.filename) | Out-Null
    }
}

try {
    $login = Invoke-Json POST "/api/auth/login" @{ studentNo = $AdminStudentNo; password = $AdminPassword } -Token $null
    Assert-True ([bool]$login.token) "管理员登录没有返回 token"
    $script:AdminToken = $login.token
    $script:EffectiveAdminPassword = $AdminPassword
    if ($login.mustChangePassword -eq $true) {
        $script:EffectiveAdminPassword = "SmokeAdmin-2026"
        Invoke-Json POST "/api/auth/change-password" @{
            oldPassword = $AdminPassword
            newPassword = $script:EffectiveAdminPassword
        } | Out-Null
        $login = Invoke-Json POST "/api/auth/login" @{
            studentNo = $AdminStudentNo
            password = $script:EffectiveAdminPassword
        } -Token $null
        $script:AdminToken = $login.token
        Add-Result "初始管理员改密" "已满足高权限操作要求"
    }
    Add-Result "管理员登录" "$($login.name) / $($login.role)"

    $health = Invoke-Json GET "/api/health" -Token $null
    Add-Result "健康检查" ($health | ConvertTo-Json -Compress)

    $baseline = Invoke-Json POST "/api/maintenance/backups"
    Remember-Backup $baseline
    $baselinePath = New-TempPath "baseline-backup.zip"
    Invoke-Download "/api/maintenance/backups/$($baseline.filename)" $baselinePath | Out-Null
    Assert-ZipBackup $baselinePath
    Add-Result "基线备份" $baseline.filename

    $suffix = (Get-Date -Format "MMddHHmmss")
    $memberNo = "9901$suffix"
    $ministerNo = "9902$suffix"
    $importNo = "9903$suffix"
    $memberName = "烟测成员$suffix"
    $ministerName = "烟测部长$suffix"
    $importName = "烟测导入$suffix"

    $member = Invoke-Json POST "/api/users" @{
        studentNo = $memberNo; name = $memberName; role = "MEMBER"; phone = "13000000000";
        major = "计算机协会测试"; grade = "2025级"; qq = "10000"
    }
    Assert-True ($member.studentNo -eq $memberNo) "创建成员返回不匹配"
    $minister = Invoke-Json POST "/api/users" @{
        studentNo = $ministerNo; name = $ministerName; role = "MINISTER"; phone = "13000000001";
        major = "计算机协会测试"; grade = "2025级"; qq = "10001"
    }
    Add-Result "成员创建" "$memberNo / $ministerNo"

    $memberImportPath = New-TempPath "member-import.xlsx"
    New-SimpleXlsx $memberImportPath @(
        @("姓名", "学号", "联系方式", "学院", "年级", "QQ"),
        @($importName, $importNo, "13000000002", "计算机协会测试", "2025级", "10002")
    ) | Out-Null
    $importResult = Invoke-Upload "/api/users/import" $memberImportPath
    Assert-True ($importResult.created -ge 1) "成员导入没有新增记录"
    Add-Result "成员批量导入" "created=$($importResult.created), skipped=$($importResult.skipped)"

    $users = Invoke-Json GET "/api/users?keyword=$suffix"
    Assert-True (@($users).Count -ge 3) "成员搜索没有找到临时用户"
    $page = Invoke-Json GET "/api/users/page?keyword=$suffix&page=1&pageSize=10"
    Assert-True ($page.total -ge 3) "成员分页没有找到临时用户"
    Invoke-Json GET "/api/users/grades" | Out-Null
    $updatedMember = Invoke-Json PUT "/api/users/$($member.id)" @{
        name = "$memberName-改"; role = "MEMBER"; status = "ACTIVE"; phone = "13000000999";
        major = "计算机协会测试"; grade = "2026级"; qq = "20000"; reason = "烟测修改成员"
    }
    Assert-True ($updatedMember.grade -eq "2026级") "成员更新未生效"
    Invoke-Json POST "/api/users/$($member.id)/reset-password" @{ newPassword = $null; reason = "烟测重置密码" } | Out-Null
    $bulk = Invoke-Json PUT "/api/users/bulk-status" @{ ids = @($minister.id); status = "DISABLED"; reason = "烟测停用" }
    Assert-True ($bulk.updated -eq 1) "批量停用未更新"
    Invoke-Json PUT "/api/users/bulk-status" @{ ids = @($minister.id); status = "ACTIVE"; reason = "烟测启用" } | Out-Null
    Add-Result "成员查询/更新/重置/批量状态" "pageTotal=$($page.total)"

    $memberLogin = Invoke-Json POST "/api/auth/login" @{ studentNo = $memberNo; password = (Get-DefaultPassword $memberNo) } -Token $null
    Invoke-Json POST "/api/auth/change-password" @{ oldPassword = (Get-DefaultPassword $memberNo); newPassword = "Smoke$suffix" } -Token $memberLogin.token | Out-Null
    $memberLogin = Invoke-Json POST "/api/auth/login" @{ studentNo = $memberNo; password = "Smoke$suffix" } -Token $null
    Invoke-Json PUT "/api/me/profile" @{ phone = "13100000000"; major = "个人资料烟测"; grade = "2026级"; qq = "30000" } -Token $memberLogin.token | Out-Null
    Invoke-Json POST "/api/auth/logout" @{} -Token $memberLogin.token | Out-Null
    Add-Result "个人资料/改密/退出登录"

    Invoke-Json GET "/api/public/duty-periods" -Token $null | Out-Null
    $weekdays = @(Invoke-Json GET "/api/settings/weekdays")
    $enabledWeekdays = @($weekdays | Where-Object { $_.enabled -eq $true -or $_.enabled -eq 1 } | ForEach-Object { [int]$_.weekday })
    if (-not $enabledWeekdays.Count) {
        $todayWeekday = [int](Get-Date).DayOfWeek
        if ($todayWeekday -eq 0) { $todayWeekday = 7 }
        $enabledWeekdays = @($todayWeekday)
    }
    Invoke-Json PUT "/api/settings/weekdays" @{ enabledWeekdays = $enabledWeekdays } | Out-Null
    Invoke-Json GET "/api/public/duty-weekdays" -Token $null | Out-Null
    $periods = @(Invoke-Json GET "/api/settings/duty-periods")
    if (-not $periods.Count) {
        $periods = @(Invoke-Json PUT "/api/settings/duty-periods" @{ periods = @(@{ startTime = "14:00"; endTime = "16:00" }) })
    } else {
        $payloadPeriods = @($periods | ForEach-Object { @{ startTime = $_.startTime; endTime = $_.endTime } })
        Invoke-Json PUT "/api/settings/duty-periods" @{ periods = $payloadPeriods } | Out-Null
    }
    Add-Result "值班星期/时段设置" "enabled=$($enabledWeekdays -join ','), periods=$(@($periods).Count)"

    $scheduleTemplate = New-TempPath "schedule-import-template.xlsx"
    Invoke-Download "/api/schedules/import-template" $scheduleTemplate | Out-Null
    Assert-Xlsx $scheduleTemplate
    $blankSchedulePreview = Invoke-Upload "/api/schedules/import/preview" $scheduleTemplate
    Assert-True ($blankSchedulePreview.valid -eq $false) "空白排班模板不应通过导入校验"
    Assert-True ($blankSchedulePreview.issues.Count -ge 1) "空白排班模板预览没有返回校验问题"
    $weekdayNames = @("", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    $importPeriod = @($periods)[0]
    $importPeriodText = "$(([string]$importPeriod.startTime).Substring(0, 5))-$(([string]$importPeriod.endTime).Substring(0, 5))"
    $scheduleImportFile = New-TempPath "schedule-import-valid.xlsx"
    New-SimpleXlsx -Path $scheduleImportFile -Rows @(
        @("星期", "值班时段", "学号", "姓名"),
        @($weekdayNames[[int]$enabledWeekdays[0]], $importPeriodText, $ministerNo, $ministerName)
    ) | Out-Null
    $validSchedulePreview = Invoke-Upload "/api/schedules/import/preview" $scheduleImportFile
    Assert-True ($validSchedulePreview.valid -eq $true) "合法排班文件未通过预览"
    $scheduleImportResult = Invoke-Upload "/api/schedules/import" $scheduleImportFile
    Assert-True ($scheduleImportResult.assignedMembers -eq 1) "排班导入人数不正确"
    Add-Result "排班模板/预览/确认导入" "groups=$($scheduleImportResult.replacedGroups)"

    $lookup = Invoke-Json GET "/api/public/attendance/lookup/$memberNo" -Token $null
    Assert-True $lookup.exists "公共查找临时成员失败"
    Invoke-Json GET "/api/public/attendance/lookup?query=$memberName" -Token $null | Out-Null
    $checkInRequestId = "smoke-$suffix-check-in"
    $checkIn = Invoke-Json POST "/api/public/attendance/submit" @{ studentNo = $memberNo; requestId = $checkInRequestId } -Token $null
    $checkInRetry = Invoke-Json POST "/api/public/attendance/submit" @{ studentNo = $memberNo; requestId = $checkInRequestId } -Token $null
    Assert-True ($checkInRetry.recordId -eq $checkIn.recordId -and $checkInRetry.action -eq "CHECK_IN") "重复签到请求未返回原始结果"
    $checkOut = Invoke-Json POST "/api/public/attendance/submit" @{ studentNo = $memberNo; requestId = "smoke-$suffix-check-out" } -Token $null
    Assert-True ($checkIn.recordId -eq $checkOut.recordId) "签到签退记录 ID 不一致"
    $today = Get-Date -Format "yyyy-MM-dd"
    Invoke-Json GET "/api/attendance/open?from=$today&to=$today" | Out-Null
    $records = @(Invoke-Json GET "/api/attendance?from=$today&to=$today&studentNo=$memberNo")
    Assert-True ($records.Count -ge 1) "后台记录查询未找到公共签到记录"
    $pending = @(Invoke-Json GET "/api/attendance/reviews/pending")
    Assert-True (@($pending | Where-Object { $_.id -eq $checkIn.recordId }).Count -ge 1) "待审核列表未找到公共签到记录"
    $bulkReview = Invoke-Json POST "/api/attendance/reviews/bulk" @{ ids = @($checkIn.recordId); part = "ALL" }
    Assert-True ($bulkReview.reviewed -ge 2) "批量审核没有通过签到签退"
    Add-Result "公共签到/签退/审核" "record=$($checkIn.recordId)"

    $manualDate = Get-EnabledWeekdayDate $enabledWeekdays[0]
    $manualIn = $manualDate.AddHours(9)
    $manualOut = $manualDate.AddHours(11)
    $manual = Invoke-Json POST "/api/attendance/manual" @{
        studentNo = $memberNo
        checkInTime = $manualIn.ToString("yyyy-MM-ddTHH:mm:ss")
        checkOutTime = $manualOut.ToString("yyyy-MM-ddTHH:mm:ss")
        reason = "烟测手动新增"
    }
    $manualUpdated = Invoke-Json PUT "/api/attendance/$($manual.id)/manual" @{
        checkInTime = $manualIn.AddMinutes(5).ToString("yyyy-MM-ddTHH:mm:ss")
        checkOutTime = $manualOut.AddMinutes(5).ToString("yyyy-MM-ddTHH:mm:ss")
        checkInStatus = "AUTO_APPROVED"
        checkOutStatus = "AUTO_APPROVED"
        reason = "烟测手动修改"
    }
    Assert-True ($manualUpdated.id -eq $manual.id) "手动记录修改返回不匹配"
    Invoke-Json DELETE "/api/attendance/$($manual.id)" | Out-Null
    Add-Result "后台手动记录新增/修改/删除" "manual=$($manual.id)"

    Invoke-Json GET "/api/stats/dashboard" | Out-Null
    Invoke-Json GET "/api/stats/summary?from=$today&to=$today" | Out-Null
    Invoke-Json GET "/api/stats/weekly-detail?from=$today&to=$today" | Out-Null
    $statsExport = New-TempPath "stats.xlsx"
    Invoke-Download "/api/stats/export?from=$today&to=$today" $statsExport | Out-Null
    Assert-Xlsx $statsExport
    Add-Result "统计看板/汇总/周明细/导出"

    $training = Invoke-Json POST "/api/trainings" @{
        title = "烟测培训$suffix"; trainingDate = $today; startTime = "10:00"; endTime = "11:30";
        location = "烟测教室"; speaker = "$memberName-改"; description = "烟测培训"; status = "PLANNED"
    }
    $template = New-TempPath "training-template.xlsx"
    Invoke-Download "/api/trainings/$($training.id)/participants/import-template" $template | Out-Null
    Assert-Xlsx $template
    $trainingImport = Invoke-Upload "/api/trainings/$($training.id)/participants/import" $template
    Assert-True (($trainingImport.created + $trainingImport.updated) -ge 1) "培训模板导入没有写入参与记录"
    $participant = Invoke-Json POST "/api/trainings/$($training.id)/participants" @{
        studentNo = $importNo; name = $importName; durationHours = 1.25; remark = "烟测手动参与"
    }
    Invoke-Json PUT "/api/trainings/$($training.id)/participants/$($participant.id)" @{
        studentNo = $importNo; name = $importName; durationHours = 1.5; remark = "烟测更新参与"
    } | Out-Null
    Invoke-Json GET "/api/trainings/$($training.id)/participants" | Out-Null
    Invoke-Json DELETE "/api/trainings/$($training.id)/participants/$($participant.id)" | Out-Null
    $trainingList = @(Invoke-Json GET "/api/trainings?keyword=$suffix&from=$today&to=$today")
    Assert-True ($trainingList.Count -ge 1) "培训列表未找到新培训"
    $trainingExport = New-TempPath "training-session.xlsx"
    Invoke-Download "/api/trainings/$($training.id)/export" $trainingExport | Out-Null
    Assert-Xlsx $trainingExport
    $trainingSummary = New-TempPath "training-summary.xlsx"
    Invoke-Download "/api/trainings/export?from=$today&to=$today" $trainingSummary | Out-Null
    Assert-Xlsx $trainingSummary
    $memberLoginAfterPasswordChange = Invoke-Json POST "/api/auth/login" @{ studentNo = $memberNo; password = "Smoke$suffix" } -Token $null
    $trainingHours = Invoke-Json GET "/api/trainings/me/hours?from=$today&to=$today" -Token $memberLoginAfterPasswordChange.token
    Assert-True ($trainingHours.trainingCount -ge 1) "个人培训时长没有统计到导入记录"
    Invoke-Json DELETE "/api/trainings/$($training.id)" | Out-Null
    Add-Result "培训创建/模板/导入/参与维护/导出/归档" "training=$($training.id)"

    $period = @($periods)[0]
    $todayWeekday = [int](Get-Date).DayOfWeek
    if ($todayWeekday -eq 0) { $todayWeekday = 7 }
    $schedule = Invoke-Json POST "/api/schedules" @{
        weekday = $todayWeekday
        startTime = $period.startTime
        endTime = $period.endTime
        title = "部长值班"
        location = "协会办公室"
        note = "烟测排班"
        enabled = $true
        assignees = @(@{ studentNo = $ministerNo; name = $ministerName })
    }
    Invoke-Json PUT "/api/schedules/$($schedule.id)" @{
        weekday = $todayWeekday
        startTime = $period.startTime
        endTime = $period.endTime
        title = "部长值班"
        location = "协会办公室"
        note = "烟测排班更新"
        enabled = $true
        assignees = @(
            @{ studentNo = $ministerNo; name = $ministerName },
            @{ studentNo = $memberNo; name = "$memberName-改" }
        )
    } | Out-Null
    Invoke-Json GET "/api/schedules" | Out-Null
    Invoke-Json GET "/api/public/schedules/today?date=$today" -Token $null | Out-Null
    Invoke-Json GET "/api/public/schedules/week?date=$today" -Token $null | Out-Null
    Invoke-Json DELETE "/api/schedules/$($schedule.id)" | Out-Null
    Add-Result "排班创建/更新/公开今日周表/归档" "schedule=$($schedule.id)"

    $repair = Invoke-Json POST "/api/repairs" @{
        agreementType = "PERSONAL_DEVICE"; ownerName = "烟测送修$suffix"; ownerPhone = "13000000003";
        deviceType = "笔记本电脑"; deviceBrand = "ThinkPad"; deviceModel = "T14";
        accessories = "电源适配器"; faultDescription = "系统无法启动"; serviceDescription = "初步检查";
        dataBackupConfirmed = $true; riskAcknowledged = $true; privacyAcknowledged = $true;
        status = "REPAIRING"; receivedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss");
        handlerName = $ministerName; remark = "烟测维修"
    }
    Invoke-Json PUT "/api/repairs/$($repair.id)" @{
        agreementType = "PUBLIC_DEVICE"; ownerName = "烟测送修$suffix"; ownerPhone = "13000000003";
        deviceType = "台式机"; deviceBrand = "Lenovo"; deviceModel = "M";
        accessories = "无"; faultDescription = "无法开机"; serviceDescription = "已完成烟测";
        dataBackupConfirmed = $true; riskAcknowledged = $true; privacyAcknowledged = $true;
        status = "COMPLETED"; receivedAt = $repair.receivedAt; completedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss");
        handlerName = $ministerName; remark = "烟测维修完成"
    } | Out-Null
    $repairList = @(Invoke-Json GET "/api/repairs?keyword=$suffix&status=ALL&from=$today&to=$today")
    Assert-True ($repairList.Count -ge 1) "维修列表未找到新维修"
    $agreementPath = New-TempPath "repair-agreement.html"
    Invoke-Download "/api/repairs/$($repair.id)/agreement" $agreementPath | Out-Null
    Assert-True ((Get-Content -LiteralPath $agreementPath -Raw) -like "*$($repair.caseNo)*") "维修协议未包含编号"
    $repairExport = New-TempPath "repairs.xlsx"
    Invoke-Download "/api/repairs/export?status=ALL&from=$today&to=$today" $repairExport | Out-Null
    Assert-Xlsx $repairExport
    Invoke-Json DELETE "/api/repairs/$($repair.id)" | Out-Null
    $repairAfterDelete = @(Invoke-Json GET "/api/repairs?keyword=$suffix&status=ALL&from=$today&to=$today")
    Assert-True ($repairAfterDelete.Count -eq 0) "软删除后的维修事务仍出现在普通列表"
    $repairRecycle = @(Invoke-Json GET "/api/repairs/recycle-bin")
    Assert-True (@($repairRecycle | Where-Object { $_.id -eq $repair.id }).Count -eq 1) "维修回收站未找到软删除事务"
    Invoke-Json POST "/api/repairs/$($repair.id)/restore" @{} | Out-Null
    Add-Result "维修创建/更新/协议/导出/回收站恢复" "case=$($repair.caseNo)"

    $ministerLogin = Invoke-Json POST "/api/auth/login" @{ studentNo = $ministerNo; password = (Get-DefaultPassword $ministerNo) } -Token $null
    Invoke-Json POST "/api/auth/change-password" @{
        oldPassword = (Get-DefaultPassword $ministerNo)
        newPassword = "Minister$suffix"
    } -Token $ministerLogin.token | Out-Null
    $ministerLogin = Invoke-Json POST "/api/auth/login" @{ studentNo = $ministerNo; password = "Minister$suffix" } -Token $null
    Invoke-Json GET "/api/repairs?status=ALL" -Token $ministerLogin.token | Out-Null
    Invoke-Json DELETE "/api/repairs/$($repair.id)" -Token $ministerLogin.token -ExpectedStatus @(403) | Out-Null
    Invoke-Json GET "/api/repairs/recycle-bin" -Token $ministerLogin.token -ExpectedStatus @(403) | Out-Null
    Invoke-Json GET "/api/trainings" -Token $ministerLogin.token -ExpectedStatus @(403) | Out-Null
    Invoke-Download "/api/repairs/export?status=ALL" (New-TempPath "minister-repair-export.xlsx") -Token $ministerLogin.token -ExpectedStatus @(403) | Out-Null
    Invoke-Json GET "/api/logs" -Token $ministerLogin.token -ExpectedStatus @(403) | Out-Null
    Invoke-Json GET "/api/exports/options" -Token $ministerLogin.token -ExpectedStatus @(403) | Out-Null
    Invoke-Json POST "/api/maintenance/backups" @{} -Token $ministerLogin.token -ExpectedStatus @(403) | Out-Null
    Add-Result "部长权限边界" "维修可看，维修删除/回收站/培训/导出/日志/备份不可用"

    Invoke-Json DELETE "/api/repairs/$($repair.id)" | Out-Null
    $purgeResult = Invoke-Json POST "/api/repairs/$($repair.id)/purge" @{ caseNo = $repair.caseNo }
    Remember-Backup $purgeResult.safetyBackup
    Assert-True ($purgeResult.caseNo -eq $repair.caseNo) "维修永久删除返回编号不匹配"
    Add-Result "维修永久删除前自动备份" $purgeResult.safetyBackup.filename

    $maintenance = Invoke-Json GET "/api/maintenance/summary"
    Assert-True ($maintenance.datasets.Count -ge 1) "维护摘要没有数据集"
    $backupList = @(Invoke-Json GET "/api/maintenance/backups")
    Assert-True ($backupList.Count -ge 1) "备份列表为空"
    $backupAgain = Invoke-Json POST "/api/maintenance/backups"
    Remember-Backup $backupAgain
    $backupAgainPath = New-TempPath "backup-again.zip"
    Invoke-Download "/api/maintenance/backups/$($backupAgain.filename)" $backupAgainPath | Out-Null
    Assert-ZipBackup $backupAgainPath
    Add-Result "数据中心摘要/备份列表/备份下载" $backupAgain.filename

    $customOptions = Invoke-Json GET "/api/exports/options"
    Assert-True ($customOptions.sources.Count -ge 6) "管理员自定义导出数据源不完整"
    $customExport = New-TempPath "custom-members.xlsx"
    Invoke-JsonDownload "/api/exports/excel" $customExport @{
        source = "members"
        fields = @("name", "studentNo", "role", "status")
        filters = @{ keyword = $suffix }
        filename = "烟测成员清单"
    } | Out-Null
    Assert-Xlsx $customExport
    Add-Result "自定义 Excel 导出" "sources=$($customOptions.sources.Count)"

    $logs = Invoke-Json GET "/api/logs?page=1&pageSize=5"
    Assert-True ($logs.total -ge 1) "操作日志没有记录"
    $logsExport = New-TempPath "logs.xlsx"
    Invoke-Download "/api/logs/export" $logsExport | Out-Null
    Assert-Xlsx $logsExport
    Add-Result "操作日志查询/导出" "total=$($logs.total)"

    if (-not $SkipRestore) {
        $clearResult = Invoke-Json DELETE "/api/logs"
        Remember-Backup $clearResult.safetyBackup
        Assert-True ($clearResult.deleted -ge 0) "日志清理返回异常"
        $restoreResult = Invoke-Upload "/api/maintenance/backups/restore" $baselinePath
        Remember-Backup $restoreResult.safetyBackup
        Assert-True ($restoreResult.totalRows -gt 0) "恢复备份没有恢复任何行"
        $loginAfterRestore = Invoke-Json POST "/api/auth/login" @{ studentNo = $AdminStudentNo; password = $script:EffectiveAdminPassword } -Token $null
        $script:AdminToken = $loginAfterRestore.token
        $restoredSearch = @(Invoke-Json GET "/api/users?keyword=$suffix")
        Assert-True ($restoredSearch.Count -eq 0) "恢复基线后仍能查到烟测用户"
        Add-Result "日志清空/备份恢复/令牌重登" "restoredRows=$($restoreResult.totalRows)"
    }

    foreach ($filename in @($script:CreatedBackups | Select-Object -Unique)) {
        try {
            Invoke-Json DELETE "/api/maintenance/backups/$filename" | Out-Null
        } catch {
            Write-Warning "删除烟测备份失败：$filename；$($_.Exception.Message)"
        }
    }
    Add-Result "烟测备份清理" "count=$(@($script:CreatedBackups | Select-Object -Unique).Count)"

    Write-Host ""
    Write-Host "FULL_SMOKE_TEST_OK steps=$($script:Results.Count)"
} finally {
    foreach ($file in $script:TempFiles) {
        try {
            if (Test-Path -LiteralPath $file) {
                Remove-Item -LiteralPath $file -Force -Recurse
            }
        } catch {
            Write-Warning "临时文件清理失败：$file"
        }
    }
}
