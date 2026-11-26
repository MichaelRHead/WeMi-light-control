package org.suppressingfire.wemi.network

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local raw-ZIP backup store for on-device `rules.db` archives.
 *
 * Stores backups under `Context.noBackupFilesDir/hardware_rules_backups` (migrating any legacy
 * files from `filesDir/hardware_rules_backups`), deduplicates consecutive identical snapshots so
 * repeated refreshes never evict pre-write backups, writes atomically via temp-file rename,
 * sorts deterministically by `(lastModified, version, name)` descending, and enforces canonical
 * directory confinement plus `MAX_ZIP_BYTES` bounds before any restore read.
 */
internal object HardwareRulesBackupStore {

    private const val BACKUP_DIR_NAME = "hardware_rules_backups"
    private val BACKUP_NAME_REGEX = Regex("rules_v(\\d+)_(\\d{8}_\\d{6})(?:_\\d{3}(?:_\\d+)?)?\\.zip")
    private val backupSeq = AtomicInteger(0)

    fun resolveBackupRootDir(appContext: Context?): File {
        val targetDir = appContext?.noBackupFilesDir?.let { File(it, BACKUP_DIR_NAME) }
            ?: appContext?.filesDir?.let { File(it, BACKUP_DIR_NAME) }
            ?: File(System.getProperty("java.io.tmpdir") ?: ".", "wemi_hardware_rules_backups")
        val legacyDir = appContext?.filesDir?.let { File(it, BACKUP_DIR_NAME) }
        migrateLegacyBackupsIfNeeded(legacyDir, targetDir)
        return targetDir
    }

    fun sanitizeBackupDeviceKey(deviceKey: String): String =
        deviceKey.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "unknown_switch" }

    fun parseBackupVersionFromName(fileName: String): Int =
        BACKUP_NAME_REGEX.matchEntire(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    fun listSortedBackupFiles(deviceDir: File): List<File> =
        deviceDir.listFiles { f -> f.isFile && f.extension == "zip" && f.length() > 0L }
            .orEmpty()
            .sortedWith(
                compareByDescending<File> { it.lastModified() }
                    .thenByDescending { parseBackupVersionFromName(it.name) }
                    .thenByDescending { it.name }
            )

    fun saveRawZipBackup(
        backupRootDir: File,
        deviceKey: String,
        dbVersion: Int,
        rawZipBytes: ByteArray,
        maxRetainedPerDevice: Int = 5
    ): File? {
        if (rawZipBytes.isEmpty() || rawZipBytes.size > HardwareRulesDbCodec.MAX_ZIP_BYTES) return null
        val deviceDir = File(backupRootDir, sanitizeBackupDeviceKey(deviceKey)).apply { mkdirs() }
        val newest = listSortedBackupFiles(deviceDir).firstOrNull()
        if (newest != null &&
            newest.length() == rawZipBytes.size.toLong() &&
            newest.length() <= HardwareRulesDbCodec.MAX_ZIP_BYTES.toLong() &&
            runCatching { newest.readBytes().contentEquals(rawZipBytes) }.getOrDefault(false)
        ) {
            return newest
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val seq = String.format(Locale.US, "%04d", backupSeq.incrementAndGet() % 10_000)
        val outFile = File(deviceDir, "rules_v${dbVersion}_${stamp}_$seq.zip")
        val tmpFile = File(deviceDir, ".${outFile.name}.tmp")
        return runCatching {
            tmpFile.writeBytes(rawZipBytes)
            if (!tmpFile.renameTo(outFile)) {
                outFile.writeBytes(rawZipBytes)
                runCatching { tmpFile.delete() }
            }
            listSortedBackupFiles(deviceDir)
                .drop(maxRetainedPerDevice.coerceAtLeast(1))
                .forEach { stale -> runCatching { stale.delete() } }
            outFile
        }.onFailure {
            runCatching { tmpFile.delete() }
        }.getOrNull()
    }

    fun listBackupsForDevice(backupRootDir: File, deviceKey: String): List<HardwareRulesBackupEntry> {
        val deviceDir = File(backupRootDir, sanitizeBackupDeviceKey(deviceKey))
        if (!deviceDir.isDirectory) return emptyList()
        val displayFmt = SimpleDateFormat("MMM d, h:mm a", Locale.US)
        val parseFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return listSortedBackupFiles(deviceDir).map { file ->
            val match = BACKUP_NAME_REGEX.matchEntire(file.name)
            val ver = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val stampDate = match?.groupValues?.getOrNull(2)?.let { raw ->
                runCatching { parseFmt.parse(raw) }.getOrNull()
            } ?: Date(file.lastModified())
            HardwareRulesBackupEntry(
                filePath = file.absolutePath,
                dbVersion = ver,
                createdAtLabel = displayFmt.format(stampDate),
                sizeBytes = file.length()
            )
        }
    }

    fun resolveConfinedBackupFile(backupRootDir: File, backupFile: File): Pair<File?, String?> {
        val canonicalRoot = runCatching { backupRootDir.canonicalFile }.getOrNull()
        val canonicalBackup = runCatching { backupFile.canonicalFile }.getOrNull()
        val allowedPrefix = canonicalRoot?.path?.let { if (it.endsWith(File.separator)) it else "$it${File.separator}" }
        if (canonicalRoot == null || canonicalBackup == null || allowedPrefix == null ||
            !canonicalBackup.path.startsWith(allowedPrefix)
        ) {
            return null to "Selected backup path is outside the app backup directory"
        }
        if (!canonicalBackup.isFile || canonicalBackup.length() <= 0L) {
            return null to "Selected backup file is missing or empty"
        }
        if (canonicalBackup.length() > HardwareRulesDbCodec.MAX_ZIP_BYTES.toLong()) {
            return null to "Selected backup archive exceeds safe 2 MiB limit"
        }
        return canonicalBackup to null
    }

    fun clearDeviceBackups(appContext: Context?, deviceKey: String) {
        val sanitizedKey = sanitizeBackupDeviceKey(deviceKey)
        runCatching { File(resolveBackupRootDir(appContext), sanitizedKey).deleteRecursively() }
        appContext?.filesDir?.let { runCatching { File(File(it, BACKUP_DIR_NAME), sanitizedKey).deleteRecursively() } }
    }

    fun clearAllBackups(appContext: Context?) {
        runCatching { resolveBackupRootDir(appContext).deleteRecursively() }
        appContext?.filesDir?.let { runCatching { File(it, BACKUP_DIR_NAME).deleteRecursively() } }
    }

    private fun migrateLegacyBackupsIfNeeded(legacyDir: File?, targetDir: File) {
        if (legacyDir == null || legacyDir == targetDir || !legacyDir.isDirectory) return
        runCatching {
            legacyDir.listFiles { f -> f.isDirectory }.orEmpty().forEach { srcDeviceDir ->
                val dstDeviceDir = File(targetDir, srcDeviceDir.name).apply { mkdirs() }
                srcDeviceDir.listFiles { f -> f.isFile && f.extension == "zip" }.orEmpty().forEach { srcZip ->
                    val dstZip = File(dstDeviceDir, srcZip.name)
                    if (!dstZip.exists()) {
                        if (!srcZip.renameTo(dstZip)) {
                            runCatching {
                                srcZip.copyTo(dstZip, overwrite = false)
                                srcZip.delete()
                            }
                        }
                    } else {
                        runCatching { srcZip.delete() }
                    }
                }
                if (srcDeviceDir.listFiles().isNullOrEmpty()) runCatching { srcDeviceDir.delete() }
            }
            if (legacyDir.listFiles().isNullOrEmpty()) runCatching { legacyDir.delete() }
        }
    }
}
