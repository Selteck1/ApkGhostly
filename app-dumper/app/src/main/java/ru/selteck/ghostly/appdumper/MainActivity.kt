package ru.selteck.ghostly.appdumper

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private data class InstalledApp(
        val info: ApplicationInfo,
        val label: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val isSystem: Boolean,
        val apkPaths: List<String>,
        val apkBytes: Long
    )

    private data class ExportResult(val success: Boolean, val message: String)

    private val prefs by lazy { getSharedPreferences("ghostly_app_dumper", Context.MODE_PRIVATE) }
    private val installedApps = mutableListOf<InstalledApp>()
    private var visibleApps: List<InstalledApp> = emptyList()
    private var selectedApp: InstalledApp? = null
    private var includeSystemApps = false
    private var exporting = false

    private lateinit var searchField: EditText
    private lateinit var systemSwitch: Switch
    private lateinit var countLabel: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var selectedLabel: TextView
    private lateinit var selectedPackage: TextView
    private lateinit var downloadButton: Button
    private lateinit var refreshButton: Button
    private lateinit var appList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C_BG
        window.navigationBarColor = C_BG
        includeSystemApps = prefs.getBoolean(KEY_SYSTEM, false)
        buildUi()
        loadApps()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_BG)
            setPadding(dp(18), dp(20), dp(18), dp(12))
        }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val emblem = TextView(this).apply {
            text = "◈"
            textSize = 29f
            setTextColor(C_ACCENT)
            gravity = Gravity.CENTER
            background = rounded(C_PANEL, C_STROKE, 17)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        }
        val brandCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brandCopy.addView(label("GHOSTLY DUMP", 21f, C_TEXT, true))
        brandCopy.addView(label("INSTALLED APK EXPORTER", 10f, C_MUTED, true).apply {
            letterSpacing = 0.14f
        }, margin(top = 3))
        brandRow.addView(emblem)
        brandRow.addView(brandCopy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        refreshButton = Button(this).apply {
            text = "↻"
            textSize = 23f
            isAllCaps = false
            setTextColor(C_TEXT)
            background = rounded(C_PANEL, C_STROKE, 14)
            minWidth = dp(48)
            minimumWidth = dp(48)
            minHeight = dp(48)
            setOnClickListener { loadApps() }
        }
        brandRow.addView(refreshButton)
        root.addView(brandRow)

        root.addView(label("Выбери приложение", 27f, C_TEXT, true), margin(top = 22, bottom = 5))
        root.addView(label("Распакуй папки ресурсов, assets, res и lib из всех установленных APK-частей.", 13f, C_MUTED), margin(bottom = 6))
        root.addView(label("Приватные данные игры и скачанные после установки файлы Android не открывает обычному приложению.", 11f, C_MUTED), margin(bottom = 14))

        searchField = EditText(this).apply {
            hint = "Поиск по названию или package name"
            textSize = 14f
            setTextColor(C_TEXT)
            setHintTextColor(C_MUTED)
            isSingleLine = true
            setPadding(dp(14), dp(3), dp(14), dp(3))
            background = rounded(C_INPUT, C_STROKE, 14)
        }
        root.addView(searchField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            bottomMargin = dp(8)
        })

        systemSwitch = Switch(this).apply {
            text = "Показывать системные приложения"
            textSize = 13f
            setTextColor(C_MUTED)
            isChecked = includeSystemApps
            thumbTintList = android.content.res.ColorStateList.valueOf(C_TEXT)
            trackTintList = android.content.res.ColorStateList.valueOf(C_ACCENT)
        }
        root.addView(systemSwitch, margin(bottom = 6))
        countLabel = label("Загрузка списка…", 12f, C_ACCENT, true)
        root.addView(countLabel, margin(bottom = 10))

        val listScroll = ScrollView(this).apply { clipToPadding = false }
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listScroll.addView(appList)
        root.addView(listScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(C_PANEL, C_STROKE, 18)
        }
        footer.addView(label("ВЫБРАННОЕ ПРИЛОЖЕНИЕ", 10f, C_ACCENT, true).apply { letterSpacing = 0.08f })
        selectedLabel = label("Ничего не выбрано", 16f, C_TEXT, true)
        footer.addView(selectedLabel, margin(top = 7))
        selectedPackage = label("Нажми на карточку в списке выше", 11f, C_MUTED)
        footer.addView(selectedPackage, margin(top = 3))
        downloadButton = Button(this).apply {
            text = "↓   СКАЧАТЬ ДАМП"
            textSize = 14f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(C_ACCENT_DARK, C_ACCENT_DARK, 14)
            minHeight = dp(54)
            isEnabled = false
            setOnClickListener { exportSelectedApp() }
        }
        footer.addView(downloadButton, margin(top = 12))
        statusTitle = label("ГОТОВО", 11f, C_GREEN, true)
        footer.addView(statusTitle, margin(top = 10))
        statusDetail = label("ZIP с извлечёнными папками сохраняется в Downloads/Ghostly APK Dumps.", 11f, C_MUTED)
        footer.addView(statusDetail, margin(top = 3))
        root.addView(footer, margin(top = 10))

        systemSwitch.setOnCheckedChangeListener { _, checked ->
            includeSystemApps = checked
            prefs.edit().putBoolean(KEY_SYSTEM, checked).apply()
            renderApps()
        }
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderApps()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        setContentView(root)
    }

    @Suppress("DEPRECATION")
    private fun loadApps() {
        if (exporting) return
        refreshButton.isEnabled = false
        countLabel.text = "Сканирование установленных приложений…"
        Thread {
            var errorMessage: String? = null
            val loaded = try {
                val applicationInfos = if (Build.VERSION.SDK_INT >= 33) {
                    packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    packageManager.getInstalledApplications(0)
                }
                applicationInfos.mapNotNull { info ->
                    try {
                        val packageName = info.packageName
                        val labelText = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() } ?: packageName
                        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                        } else {
                            packageManager.getPackageInfo(packageName, 0)
                        }
                        val paths = buildList {
                            if (info.sourceDir.isNotBlank()) add(info.sourceDir)
                            info.splitSourceDirs?.forEach { if (it.isNotBlank()) add(it) }
                        }.distinct()
                        val bytes = paths.sumOf { path -> try { File(path).length() } catch (_: Exception) { 0L } }
                        InstalledApp(
                            info, labelText, packageName, packageInfo.versionName ?: "неизвестно",
                            if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong(),
                            (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0, paths, bytes
                        )
                    } catch (_: Exception) {
                        null
                    }
                }.sortedWith(compareBy<InstalledApp> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })
            } catch (e: Exception) {
                errorMessage = e.message ?: "Android запретил чтение списка пакетов"
                emptyList()
            }
            runOnUiThread {
                installedApps.clear()
                installedApps.addAll(loaded)
                refreshButton.isEnabled = true
                renderApps()
                if (errorMessage != null) setStatus("НЕ УДАЛОСЬ ПРОЧИТАТЬ СПИСОК", errorMessage!!, false)
                else setStatus("СПИСОК ОБНОВЛЁН", "Найдено пакетов: ${loaded.size}. Выбери приложение.", true)
            }
        }.start()
    }

    private fun renderApps() {
        if (!::appList.isInitialized) return
        val query = if (::searchField.isInitialized) searchField.text.toString().trim().lowercase(Locale.ROOT) else ""
        visibleApps = installedApps.filter { app ->
            (includeSystemApps || !app.isSystem) &&
                (query.isBlank() || app.label.lowercase(Locale.ROOT).contains(query) || app.packageName.lowercase(Locale.ROOT).contains(query))
        }
        countLabel.text = "${visibleApps.size} приложений · ${if (includeSystemApps) "включая системные" else "пользовательские"}"
        appList.removeAllViews()

        if (visibleApps.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(30), dp(20), dp(30))
                background = rounded(C_PANEL, C_STROKE, 18)
            }
            empty.addView(label(if (installedApps.isEmpty()) "Список пока пуст" else "Ничего не найдено", 17f, C_TEXT, true))
            empty.addView(label("Проверь поиск или включи системные приложения.", 12f, C_MUTED).apply {
                gravity = Gravity.CENTER
            }, margin(top = 8))
            appList.addView(empty)
        } else {
            visibleApps.forEach { app -> appList.addView(createAppCard(app), margin(bottom = 8)) }
        }
        if (selectedApp != null && visibleApps.none { it.packageName == selectedApp?.packageName }) selectedApp = null
        renderSelected()
    }

    private fun createAppCard(app: InstalledApp): View {
        val selected = selectedApp?.packageName == app.packageName
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(if (selected) C_SELECTED else C_PANEL, if (selected) C_ACCENT else C_STROKE, 16)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedApp = app
                renderApps()
            }
        }
        val icon = ImageView(this).apply {
            try { setImageDrawable(app.info.loadIcon(packageManager)) }
            catch (_: Exception) { setImageResource(android.R.drawable.sym_def_app_icon) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        card.addView(icon)
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }
        textColumn.addView(label(app.label, 14f, C_TEXT, true))
        textColumn.addView(label(app.packageName, 10f, C_MUTED), margin(top = 4))
        val appType = if (app.isSystem) "Системное" else "Установленное"
        val splitInfo = if (app.apkPaths.size > 1) " · ${app.apkPaths.size} APK-частей" else " · APK"
        textColumn.addView(label("$appType · ${formatSize(app.apkBytes)}$splitInfo", 10f, C_ACCENT), margin(top = 5))
        card.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(TextView(this).apply {
            text = if (selected) "✓" else "›"
            textSize = if (selected) 23f else 27f
            setTextColor(if (selected) C_ACCENT else C_MUTED)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(25), dp(42))
        })
        return card
    }

    private fun renderSelected() {
        val app = selectedApp
        if (app == null) {
            selectedLabel.text = "Ничего не выбрано"
            selectedPackage.text = "Нажми на карточку в списке выше"
            downloadButton.text = "↓   СКАЧАТЬ ДАМП"
            downloadButton.isEnabled = false
            return
        }
        selectedLabel.text = app.label
        selectedPackage.text = "${app.packageName}\nВерсия ${app.versionName} · ${formatSize(app.apkBytes)} · ${app.apkPaths.size} APK-частей"
        downloadButton.text = "↓   РАСПАКОВАТЬ СОДЕРЖИМОЕ (.ZIP)"
        downloadButton.isEnabled = !exporting
    }

    private fun exportSelectedApp() {
        val app = selectedApp ?: return
        if (exporting) return
        val sources = app.apkPaths.filter { path -> File(path).isFile && File(path).canRead() }
        if (sources.isEmpty()) {
            setStatus("НЕТ ДОСТУПА К APK", "Android не разрешил прочитать установочный APK.", false)
            return
        }
        exporting = true
        refreshButton.isEnabled = false
        downloadButton.isEnabled = false
        downloadButton.text = "СОХРАНЕНИЕ…"
        setStatus("ЭКСПОРТ APK", "Копирование ${app.label}. Не закрывай приложение.", true)

        Thread {
            val result = try { exportPackage(app, sources) }
            catch (e: Exception) { ExportResult(false, e.message ?: "Не удалось экспортировать пакет") }
            runOnUiThread {
                exporting = false
                refreshButton.isEnabled = true
                renderSelected()
                setStatus(if (result.success) "ГОТОВО" else "ОШИБКА ЭКСПОРТА", result.message, result.success)
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun exportPackage(app: InstalledApp, sources: List<String>): ExportResult {
        val safeLabel = app.label.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_', '.', '-').ifBlank { "app" }.take(70)
        val safeVersion = app.versionName.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(32).ifBlank { "unknown" }
        val fileName = "${safeLabel}_${safeVersion}_contents.zip"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ghostly APK Dumps")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Не удалось создать файл в Downloads")

        try {
            val output = contentResolver.openOutputStream(uri, "w")
                ?: throw IOException("Не удалось открыть файл для записи")
            var extractedFiles = 0
            var extractedBytes = 0L
            var skippedEntries = 0

            output.use { stream ->
                ZipOutputStream(stream.buffered()).use { zip ->
                    val usedOutputNames = mutableSetOf<String>()
                    val sourcesSummary = mutableListOf<String>()

                    sources.forEachIndexed { index, sourcePath ->
                        val apkFile = File(sourcePath)
                        val partFolder = if (index == 0) {
                            "package/base"
                        } else {
                            "package/split_${index}_${safeSegment(apkFile.nameWithoutExtension)}"
                        }
                        sourcesSummary.add("$partFolder <= ${apkFile.name}")

                        java.util.zip.ZipFile(apkFile).use { apkZip ->
                            val entries = apkZip.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                val originalPath = safeArchivePath(entry.name)
                                if (originalPath == null) {
                                    skippedEntries++
                                    continue
                                }

                                val fullPath = uniqueZipEntryName(
                                    "$partFolder/$originalPath",
                                    entry.isDirectory,
                                    usedOutputNames
                                )
                                val outputEntry = ZipEntry(fullPath)
                                if (entry.time > 0L) outputEntry.time = entry.time
                                zip.putNextEntry(outputEntry)

                                if (!entry.isDirectory) {
                                    apkZip.getInputStream(entry).use { input ->
                                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            zip.write(buffer, 0, count)
                                            extractedBytes += count
                                        }
                                    }
                                    extractedFiles++
                                }
                                zip.closeEntry()
                            }
                        }
                    }

                    zip.putNextEntry(ZipEntry("dump-info.txt"))
                    val info = buildString {
                        appendLine("Ghostly Extracted APK Contents")
                        appendLine("App: ${app.label}")
                        appendLine("Package: ${app.packageName}")
                        appendLine("Version: ${app.versionName} (${app.versionCode})")
                        appendLine("APK parts inspected: ${sources.size}")
                        appendLine("Files extracted: $extractedFiles")
                        appendLine("Uncompressed bytes written: $extractedBytes")
                        appendLine("Unsafe entries skipped: $skippedEntries")
                        appendLine()
                        appendLine("Source APK parts:")
                        sourcesSummary.forEach { appendLine("  $it") }
                        appendLine()
                        appendLine("Each APK's own ZIP contents are unpacked under package/base/ or package/split_N_name/.")
                        appendLine("This includes resources, assets, native libraries and other entries when present in the installed APKs.")
                        appendLine("Files that are themselves proprietary archives (for example .pak or .obb files) remain as files and are not decrypted or recursively unpacked.")
                        appendLine("Private app storage, runtime-downloaded files, saved data and process memory are not included.")
                    }
                    zip.write(info.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }

            contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null)
            return ExportResult(
                true,
                "Распаковано файлов: $extractedFiles. ZIP сохранён: Downloads/Ghostly APK Dumps/$fileName"
            )
        } catch (e: Exception) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) { }
            throw e
        }
    }

    private fun safeSegment(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_', '.', '-').ifBlank { "part" }.take(70)

    /**
     * Preserve package folder paths but reject absolute paths and parent traversal
     * from an entry, so an APK entry cannot escape its own directory in the ZIP.
     */
    private fun safeArchivePath(raw: String): String? {
        val path = raw.replace('\\', '/')
        if (path.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(path)) return null
        val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/") + if (raw.endsWith("/") && parts.isNotEmpty()) "/" else ""
    }

    private fun uniqueZipEntryName(
        requested: String,
        directory: Boolean,
        usedNames: MutableSet<String>
    ): String {
        if (usedNames.add(requested)) return requested
        if (directory) return requested
        val dot = requested.lastIndexOf('.')
        val parent = if (requested.contains('/')) requested.substringBeforeLast('/') + "/" else ""
        val leaf = requested.substringAfterLast('/')
        val base = if (dot > requested.lastIndexOf('/')) leaf.substringBeforeLast('.') else leaf
        val extension = if (dot > requested.lastIndexOf('/')) leaf.substringAfterLast('.') else ""
        var suffix = 2
        while (true) {
            val candidateLeaf = if (extension.isEmpty()) "${base}_${suffix}" else "${base}_${suffix}.$extension"
            val candidate = parent + candidateLeaf
            if (usedNames.add(candidate)) return candidate
            suffix++
        }
    }

    private fun setStatus(title: String, detail: String, success: Boolean) {
        statusTitle.text = title
        statusTitle.setTextColor(if (success) C_GREEN else C_RED)
        statusDetail.text = detail
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = true
        }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_SYSTEM = "include_system_apps"
        private val C_BG = Color.rgb(11, 11, 18)
        private val C_PANEL = Color.rgb(21, 21, 33)
        private val C_INPUT = Color.rgb(30, 30, 45)
        private val C_STROKE = Color.rgb(49, 47, 69)
        private val C_SELECTED = Color.rgb(34, 28, 58)
        private val C_TEXT = Color.rgb(244, 242, 255)
        private val C_MUTED = Color.rgb(157, 156, 179)
        private val C_ACCENT = Color.rgb(167, 139, 250)
        private val C_ACCENT_DARK = Color.rgb(111, 67, 224)
        private val C_GREEN = Color.rgb(74, 222, 128)
        private val C_RED = Color.rgb(248, 113, 113)
    }
}
