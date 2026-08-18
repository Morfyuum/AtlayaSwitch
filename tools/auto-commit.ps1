# Chris Deliga / CNS Capecter NetworXs System / atlaya.capecter.com
# Licensed under the Atlaya Source-Available License v1.0 - see LICENSE
#
# Hook: Stop
# Committet automatisch alle lokalen Aenderungen in D:\AtlayaSwitch und
# sichert den aktuellen Branch zusaetzlich in ein rein lokales Bare-Backup-
# Repo (D:\AtlayaBackup\AtlayaSwitch.git, kein Netzwerk), damit unversionierte
# /nur-lokal-committete Arbeit nie durch einen `git reset --hard` o.ae.
# verloren gehen kann (gleiches Muster wie D:\Atlaya\scripts\auto-commit.ps1,
# nach dem Vorfall vom 2026-08-16).
#
# WICHTIG: pusht NIEMALS zu origin/GitHub - nur zum lokalen "backup"-Remote.
# Das eigentliche Pushen bleibt bewusst ein manueller, geprueften Schritt.
param()

$RepoPath = "D:\AtlayaSwitch"
$BackupRemote = "backup"

try {
    if (-not (Test-Path (Join-Path $RepoPath ".git"))) { exit 0 }

    $gitDir = Join-Path $RepoPath ".git"
    if (Test-Path (Join-Path $gitDir "MERGE_HEAD")) { exit 0 }
    if (Test-Path (Join-Path $gitDir "rebase-merge")) { exit 0 }
    if (Test-Path (Join-Path $gitDir "rebase-apply")) { exit 0 }

    $status = & git -C $RepoPath status --porcelain 2>$null
    if ($LASTEXITCODE -eq 0 -and $status) {
        $changedFiles = @($status | ForEach-Object { $_.Substring(3).Trim() })
        $count = $changedFiles.Count
        $preview = ($changedFiles | Select-Object -First 8) -join ", "
        if ($count -gt 8) { $preview += ", ..." }

        $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm"
        $message = "Auto-Commit: $timestamp - $count Datei(en) geaendert`n`n$preview`n`nAutomatisch erzeugt vom Stop-Hook (tools\auto-commit.ps1) - noch nicht gepusht."

        & git -C $RepoPath add -A 2>$null
        & git -C $RepoPath commit -m $message 2>$null | Out-Null
    }

    $remotes = & git -C $RepoPath remote 2>$null
    if ($remotes -contains $BackupRemote) {
        $branch = (& git -C $RepoPath branch --show-current 2>$null).Trim()
        if ($branch) {
            & git -C $RepoPath push $BackupRemote $branch 2>$null | Out-Null
        }
    }
} catch {
    # still ignorieren - Hook darf die Session nie stoeren
}
exit 0
