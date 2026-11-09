; =========================================================================
; ShiftKar — Official Inno Setup 6 Installer Script
; Styled Modern Wizard + Full Windows Apps & Features Uninstaller + Start Menu
; =========================================================================

#ifndef MyAppVersion
  #define MyAppVersion "6.7.3"
#endif

#ifndef MyArch
  #define MyArch "x64"
#endif

#ifndef MySourceDir
  #define MySourceDir "..\build\compose\binaries\main\app\ShiftKar"
#endif

#define MyAppName "ShiftKar"
#define MyAppDisplayName "شیفت‌کار"
#define MyAppPublisher "Seyed Hossein Mousavi Saeedi"
#define MyAppURL "https://alimosavi-code.github.io"
#define MyAppExeName "ShiftKar.exe"

[Setup]
AppId={{8F9E4A32-6C15-4D88-9B12-7E5A3109C673}
AppName={#MyAppDisplayName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppDisplayName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL=https://github.com/Alimosavi-Code/shiftkar-desktop
AppUpdatesURL=https://github.com/Alimosavi-Code/shiftkar-desktop/releases
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppDisplayName}
DisableProgramGroupPage=no
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputBaseFilename=ShiftKar-{#MyAppVersion}-windows-{#MyArch}-setup
SetupIconFile=ShiftKar.ico
WizardStyle=modern
WizardSizePercent=115,115
WizardImageFile=wizard-large.bmp
WizardSmallImageFile=wizard-small.bmp
WizardImageStretch=yes
Compression=lzma2/fast
SolidCompression=yes
Uninstallable=yes
CreateUninstallRegKey=yes
UninstallDisplayName={#MyAppDisplayName}
UninstallDisplayIcon={app}\{#MyAppExeName}
CloseApplications=yes
RestartApplications=no
ArchitecturesInstallIn64BitMode=x64 arm64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#MySourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
; Start Menu main application shortcut
Name: "{group}\{#MyAppDisplayName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Comment: "تقویم هوشمند نوبت‌کاری شیفت‌کار"
; Start Menu dedicated Uninstaller shortcut
Name: "{group}\حذف شیفت‌کار"; Filename: "{uninstallexe}"; IconFilename: "{app}\{#MyAppExeName}"; Comment: "حذف کامل برنامه شیفت‌کار از ویندوز"
; Optional Desktop shortcut
Name: "{autodesktop}\{#MyAppDisplayName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppDisplayName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
