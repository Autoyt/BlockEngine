param(
    [string] $Path = "src/main/resources/assets/pfp.png",
    [int] $Size = 256
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$resolved = (Resolve-Path -LiteralPath $Path).ProviderPath
$temp = "$resolved.tmp"
$source = [System.Drawing.Image]::FromFile($resolved)
$image = New-Object System.Drawing.Bitmap $Size, $Size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
try {
    $graphics = [System.Drawing.Graphics]::FromImage($image)
    try {
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.DrawImage($source, 0, 0, $Size, $Size)
    } finally {
        $graphics.Dispose()
    }

    $image.Save($temp, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $image.Dispose()
    $source.Dispose()
}

Remove-Item -LiteralPath $resolved -Force
Move-Item -LiteralPath $temp -Destination $resolved -Force
Write-Host "Resized $resolved to ${Size}x${Size}"
