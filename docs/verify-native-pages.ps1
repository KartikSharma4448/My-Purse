param([string]$Package = "$PSScriptRoot\..\app\build\outputs\apk\debug\app-debug.apk")
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Package).Path)
try {
    $results = foreach ($entry in $archive.Entries) {
        if ($entry.FullName -notmatch '(arm64-v8a|x86_64)/.+\.so$') { continue }
        $buffer = [System.IO.MemoryStream]::new()
        $stream = $entry.Open()
        try { $stream.CopyTo($buffer) } finally { $stream.Dispose() }
        $bytes = $buffer.ToArray()
        $buffer.Dispose()
        if ($bytes[0] -ne 127 -or $bytes[1] -ne 69 -or $bytes[4] -ne 2 -or $bytes[5] -ne 1) {
            throw "Unexpected ELF format: $($entry.FullName)"
        }
        $offset = [BitConverter]::ToUInt64($bytes,32)
        $step = [BitConverter]::ToUInt16($bytes,54)
        $count = [BitConverter]::ToUInt16($bytes,56)
        $alignments = for ($i=0; $i -lt $count; $i++) {
            $position = [int]($offset + $i*$step)
            if ([BitConverter]::ToUInt32($bytes,$position) -eq 1) {
                [BitConverter]::ToUInt64($bytes,$position+48)
            }
        }
        $compatible = @($alignments | Where-Object { $_ -lt 16384 }).Count -eq 0
        [pscustomobject]@{ Library=$entry.FullName; LoadAlignment=($alignments -join ','); Supports16KB=$compatible }
        if (-not $compatible) { throw "Native library needs 16 KB alignment: $($entry.FullName)" }
    }
    $results | Format-Table -AutoSize
} finally { $archive.Dispose() }
