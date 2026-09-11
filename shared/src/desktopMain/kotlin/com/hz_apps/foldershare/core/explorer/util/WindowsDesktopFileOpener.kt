package com.hz_apps.foldershare.core.explorer.util

import java.io.File

class WindowsDesktopFileOpener : DesktopFileOpener {
    override fun openFile(
        downloadUrl: String,
        mimeType: String?,
        title: String?,
        fileSize: Long?,
        rawUrl: String?
    ): Boolean {
        try {
            val extension = (title ?: downloadUrl.substringAfterLast('/', ""))
                .substringAfterLast('.', "")
                .lowercase()
                .ifEmpty { null } ?: return false

            // Do not handle html or generic files, let the browser handle them
            if (mimeType == "text/html" || extension == "html" || extension == "htm") {
                return false
            }

            // We use PowerShell with C# Add-Type to call Windows API (AssocQueryString) natively
            // to find the default executable for this file extension.
            val script = """
                ${'$'}ErrorActionPreference = 'Stop'
                Add-Type -TypeDefinition @"
                using System;
                using System.Runtime.InteropServices;
                using System.Text;
                
                public class FileAssociation {
                    [DllImport("Shlwapi.dll", CharSet = CharSet.Unicode)]
                    public static extern uint AssocQueryString(uint flags, uint str, string pszAssoc, string pszExtra, StringBuilder pszOut, ref uint pcchOut);
                
                    public static string GetExecutablePath(string extension) {
                        uint length = 0;
                        // 2 = ASSOCSTR_EXECUTABLE
                        AssocQueryString(0x00000000, 2, extension, null, null, ref length);
                        if (length == 0) return "";
                        StringBuilder sb = new StringBuilder((int)length);
                        AssocQueryString(0x00000000, 2, extension, null, sb, ref length);
                        return sb.ToString();
                    }
                }
"@
                [FileAssociation]::GetExecutablePath(".$extension")
            """.trimIndent()

            val tempScriptFile = File.createTempFile("FolderShare_Open", ".ps1")
            tempScriptFile.writeText(script)

            val process = ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", tempScriptFile.absolutePath)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            tempScriptFile.delete()

            if (output.isNotBlank()) {
                ProcessBuilder(output, downloadUrl).start()
                return true
            }
        } catch (ignored: Exception) {
        }
        return false
    }
}
