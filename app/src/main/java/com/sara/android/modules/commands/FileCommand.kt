package com.sara.android.modules.commands

import android.content.Context
import android.os.Environment
import java.io.File

class FileCommand : Command {
    override val name = "file"
    override val description = "Browse filesystem. Usage: /file [path]"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val pathArg = if (args.isNotEmpty()) args.joinToString(" ") else ""
        
        val targetPath = if (pathArg.isBlank() || pathArg == "/") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            pathArg
        }

        val targetDir = File(targetPath)

        if (!targetDir.exists()) {
            return CommandResult.Text("❌ Path does not exist: $targetPath")
        }

        if (!targetDir.canRead()) {
            return CommandResult.Text("❌ Permission denied to read: $targetPath")
        }

        if (targetDir.isFile) {
            val sizeKb = targetDir.length() / 1024
            return CommandResult.Text("📄 File: ${targetDir.name}\nSize: $sizeKb KB\nPath: $targetPath")
        }

        val files = targetDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        if (files == null) {
            return CommandResult.Text("❌ Failed to list contents of: $targetPath")
        }

        val sb = StringBuilder("📁 <b>${targetDir.absolutePath}</b>\n\n")
        
        var dirCount = 0
        var fileCount = 0
        var totalSize = 0L

        for (file in files) {
            if (file.isDirectory) {
                dirCount++
                sb.append("📁 <code>${file.name}/</code>\n")
            } else {
                fileCount++
                val sizeKb = file.length() / 1024
                totalSize += file.length()
                sb.append("📄 <code>${file.name}</code> (${sizeKb} KB)\n")
            }
        }
        
        sb.append("\n<b>Summary:</b> $dirCount dirs, $fileCount files (${totalSize / 1024 / 1024} MB)")
        return CommandResult.Text(sb.toString())
    }
}
