# Verbindet dieses Windows per WLAN-ADB mit dem Pixel (GrapheneOS).
# Aufruf: .\connect-pixel.ps1 -IpPort 192.168.2.xxx:xxxxx
#
# Die IP:Port stehen auf dem Pixel unter:
# Einstellungen -> Entwickleroptionen -> Drahtloses Debugging (Profil "Eigentuemer",
# nur dort sichtbar). Der Wert aendert sich bei jedem Neustart von Drahtlosem
# Debugging bzw. bei IP-Wechsel im WLAN - ein voll automatisches Wiederfinden
# (mDNS) ist auf diesem Geraet nicht moeglich, da GrapheneOS die dafuer noetigen
# Discovery-Broadcasts nicht dauerhaft sendet.

param(
    [Parameter(Mandatory = $true)]
    [string]$IpPort
)

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

& $adb connect $IpPort
& $adb devices -l
