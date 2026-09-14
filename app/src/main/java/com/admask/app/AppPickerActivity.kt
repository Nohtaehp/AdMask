package com.admask.app

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * 选择「遮罩生效」的目标应用（仅在开启应用过滤时生效）。
 */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var prefs: OverlayPrefs
    private lateinit var adapter: AppAdapter

    private val allApps = mutableListOf<AppEntry>()
    private val visibleApps = mutableListOf<AppEntry>()
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)
        prefs = OverlayPrefs(this)
        selected.addAll(prefs.selectedApps)

        val listView = findViewById<ListView>(R.id.list_apps)
        val search = findViewById<EditText>(R.id.et_search)
        adapter = AppAdapter(visibleApps, selected)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = visibleApps[position].packageName
            if (selected.contains(pkg)) selected.remove(pkg) else selected.add(pkg)
            adapter.notifyDataSetChanged()
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
        })

        findViewById<MaterialButton>(R.id.btn_done).setOnClickListener {
            prefs.selectedApps = selected
            finish()
        }
        findViewById<MaterialButton>(R.id.btn_clear).setOnClickListener {
            selected.clear()
            adapter.notifyDataSetChanged()
        }

        loadApps()
    }

    private fun loadApps() {
        // 加载已安装应用较慢，放到子线程执行
        Thread {
            val entries = queryApps()
            runOnUiThread {
                allApps.clear()
                allApps.addAll(entries)
                filter(findViewById<EditText>(R.id.et_search).text?.toString().orEmpty())
            }
        }.start()
    }

    private fun queryApps(): List<AppEntry> {
        val pm = packageManager
        val entries = mutableListOf<AppEntry>()
        @SuppressLint("QueryPermissionsNeeded")
        val packages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
        for (info in packages) {
            val appInfo = info.applicationInfo ?: continue
            if (info.packageName == packageName) continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = appInfo.loadLabel(pm).toString()
            entries += AppEntry(label, info.packageName, appInfo, isSystem)
        }
        entries.sortWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
        return entries
    }

    private fun filter(keyword: String) {
        val kw = keyword.trim().lowercase()
        visibleApps.clear()
        visibleApps.addAll(
            if (kw.isEmpty()) allApps
            else allApps.filter {
                it.label.lowercase().contains(kw) || it.packageName.lowercase().contains(kw)
            }
        )
        adapter.notifyDataSetChanged()
    }

    private class AppEntry(
        val label: String,
        val packageName: String,
        val appInfo: ApplicationInfo,
        val isSystem: Boolean
    )

    private class AppAdapter(
        private val items: List<AppEntry>,
        private val selected: Set<String>
    ) : BaseAdapter() {

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): AppEntry = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            val entry = getItem(position)
            val icon = view.findViewById<ImageView>(R.id.iv_icon)
            val name = view.findViewById<TextView>(R.id.tv_name)
            val pkg = view.findViewById<TextView>(R.id.tv_package)
            val check = view.findViewById<CheckBox>(R.id.cb_check)

            icon.setImageDrawable(entry.appInfo.loadIcon(parent.context.packageManager))
            name.text = entry.label
            pkg.text = entry.packageName
            check.isChecked = selected.contains(entry.packageName)
            return view
        }
    }
}
