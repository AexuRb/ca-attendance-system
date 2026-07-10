!macro preInit
  SetRegView 64
  WriteRegExpandStr HKLM "${INSTALL_REGISTRY_KEY}" InstallLocation "C:\CAAttendance\app"
  WriteRegExpandStr HKCU "${INSTALL_REGISTRY_KEY}" InstallLocation "C:\CAAttendance\app"
  SetRegView 32
  WriteRegExpandStr HKLM "${INSTALL_REGISTRY_KEY}" InstallLocation "C:\CAAttendance\app"
  WriteRegExpandStr HKCU "${INSTALL_REGISTRY_KEY}" InstallLocation "C:\CAAttendance\app"
!macroend

!macro prepareWritableDirectory directory
  CreateDirectory "$R8\${directory}"
  nsExec::ExecToLog '"$SYSDIR\icacls.exe" "$R8\${directory}" /grant "*S-1-5-32-545:(OI)(CI)M" /C'
  Pop $R7
  ${If} $R7 != 0
    MessageBox MB_ICONSTOP "Failed to prepare the application data directory: $R8\${directory}"
    Abort
  ${EndIf}
!macroend

!macro customInstall
  ${GetFileName} "$INSTDIR" $R9
  ${If} $R9 == "app"
    GetFullPathName $R8 "$INSTDIR\.."
  ${Else}
    StrCpy $R8 "$INSTDIR"
  ${EndIf}
  !insertmacro prepareWritableDirectory "data"
  !insertmacro prepareWritableDirectory "backups"
  !insertmacro prepareWritableDirectory "exports"
  !insertmacro prepareWritableDirectory "logs"
!macroend
