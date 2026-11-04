#requires -Version 5.1
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'corpus')
)

Add-Type -AssemblyName System.Drawing

$cases = @(
    @{
        File = 'fr-invoice.png'
        Font = 'Arial'
        RightToLeft = $false
        Lines = @(
            'FACTURE N° 2026-001',
            'Client : Administration DocuScan',
            'Date : 27 octobre 2026',
            'Montant HT : 12 500,00 DZD',
            'TVA : 2 375,00 DZD',
            'Total TTC : 14 875,00 DZD'
        )
    },
    @{
        File = 'en-invoice.png'
        Font = 'Arial'
        RightToLeft = $false
        Lines = @(
            'INVOICE NO. 2026-001',
            'Customer: DocuScan Administration',
            'Date: October 27, 2026',
            'Subtotal: 12,500.00 DZD',
            'Tax: 2,375.00 DZD',
            'Total: 14,875.00 DZD'
        )
    },
    @{
        File = 'ar-invoice.png'
        Font = 'Arial'
        RightToLeft = $true
        Lines = @(
            '?????? ??? 2026-001',
            '??????: ????? ?????? ?????????',
            '???????: 27 ?????? 2026',
            '??????? ??? ???????: 12500.00 ?????',
            '???????: 2375.00 ?????',
            '??????? ????????: 14875.00 ?????'
        )
    }
)

if (-not (Test-Path $OutputDirectory)) {
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
}

foreach ($case in $cases) {
    $bitmap = New-Object System.Drawing.Bitmap 1600, 760
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::White)
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    $font = New-Object System.Drawing.Font($case.Font, 42, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $brush = [System.Drawing.Brushes]::Black
    $format = New-Object System.Drawing.StringFormat
    if ($case.RightToLeft) {
        $format.FormatFlags = [System.Drawing.StringFormatFlags]::DirectionRightToLeft
        $format.Alignment = [System.Drawing.StringAlignment]::Near
    }

    $y = 70
    foreach ($line in $case.Lines) {
        $rect = New-Object System.Drawing.RectangleF 60, $y, 1480, 80
        $graphics.DrawString($line, $font, $brush, $rect, $format)
        $y += 105
    }

    $path = Join-Path $OutputDirectory $case.File
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bitmap.Dispose()
    $font.Dispose()
    $format.Dispose()
    Write-Output "Generated $path"
}
