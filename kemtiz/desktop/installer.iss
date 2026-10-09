#define AppName "Kemtiz"
#define AppVersion "2.1.0"
#define AppPublisher "Kemtiz"
#define AppExeName "Kemtiz.exe"
#define OutputFolder "..\..\dist"
#define ExeSource "..\..\dist\Kemtiz.exe"

[Setup]
AppId={{A25B86F1-9A51-4D2E-9D3B-57F9D3F6D31C}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={localappdata}\Programs\Kemtiz
DefaultGroupName=Kemtiz
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64
OutputDir={#OutputFolder}
OutputBaseFilename=Kemtiz-Setup
SetupIconFile=kemtiz.ico
UninstallDisplayIcon={app}\{#AppExeName}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no

[Tasks]
Name: "desktopicon"; Description: "Создать ярлык Kemtiz на рабочем столе"; GroupDescription: "Дополнительные действия:"; Flags: checkedonce

[Files]
Source: "{#ExeSource}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\Kemtiz"; Filename: "{app}\{#AppExeName}"
Name: "{autodesktop}\Kemtiz"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Запустить Kemtiz"; Flags: postinstall nowait skipifsilent
