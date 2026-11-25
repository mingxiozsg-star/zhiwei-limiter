package com.zhiwei.limiter.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiwei.limiter.data.AppEntry
import com.zhiwei.limiter.data.AppRepository
import com.zhiwei.limiter.data.ConfigStore
import com.zhiwei.limiter.data.ManagedApp
import com.zhiwei.limiter.service.GuardService
import com.zhiwei.limiter.shell.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ScreenTab(val title: String) {
    MANAGED("已限制"), ALL("全部应用"), LOG("日志")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimiterScreen() {
    val context = LocalContext.current
    val store = remember { ConfigStore(context) }
    val repo = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(ScreenTab.MANAGED) }
    var serviceOn by remember { mutableStateOf(store.serviceEnabled) }
    var shizukuReady by remember { mutableStateOf(false) }
    var shizukuBinder by remember { mutableStateOf(false) }
    var includeSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    val managed = remember { mutableStateListOf<ManagedApp>() }
    val installed = remember { mutableStateListOf<AppEntry>() }
    val logs = remember { mutableStateListOf<String>() }

    fun refreshAll() {
        managed.clear()
        managed.addAll(store.loadApps())
        logs.clear()
        logs.addAll(store.readLog())
        shizukuBinder = ShellExecutor.isBinderAvailable()
        shizukuReady = ShellExecutor.hasPermission()
    }

    LaunchedEffect(Unit) {
        refreshAll()
        withContext(Dispatchers.IO) {
            val list = repo.loadAll(includeSystem = false)
            withContext(Dispatchers.Main) {
                installed.clear()
                installed.addAll(list)
            }
        }
        // 周期性刷新日志，让用户能看到强停记录实时出现
        while (true) {
            delay(1500)
            shizukuBinder = ShellExecutor.isBinderAvailable()
            shizukuReady = ShellExecutor.hasPermission()
            if (tab == ScreenTab.LOG) {
                val newLogs = withContext(Dispatchers.IO) { store.readLog() }
                if (newLogs.size != logs.size) {
                    logs.clear()
                    logs.addAll(newLogs)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("知微应用限制", fontWeight = FontWeight.SemiBold)
                        Text(
                            "离开前台即强停 · 未授权自启动",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            refreshAll()
                            val list = withContext(Dispatchers.IO) {
                                repo.loadAll(includeSystem)
                            }
                            installed.clear()
                            installed.addAll(list)
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            StatusCard(
                shizukuBinder = shizukuBinder,
                shizukuReady = shizukuReady,
                serviceOn = serviceOn,
                managedCount = managed.size,
                onRequestPermission = {
                    ShellExecutor.requestPermission()
                    scope.launch {
                        delay(1200)
                        shizukuReady = ShellExecutor.hasPermission()
                    }
                },
                onToggleService = { on ->
                    if (on) {
                        if (!shizukuReady) {
                            ShellExecutor.requestPermission()
                        } else {
                            GuardService.start(context)
                            serviceOn = true
                            store.serviceEnabled = true
                        }
                    } else {
                        GuardService.stop(context)
                        serviceOn = false
                        store.serviceEnabled = false
                    }
                }
            )

            TabRow(selectedTabIndex = tab.ordinal) {
                ScreenTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = {
                            Text(
                                when (t) {
                                    ScreenTab.MANAGED -> "${t.title}(${managed.size})"
                                    else -> t.title
                                }
                            )
                        }
                    )
                }
            }

            when (tab) {
                ScreenTab.MANAGED -> ManagedList(
                    modifier = Modifier.weight(1f),
                    apps = managed,
                    onToggleEnabled = { app, on ->
                        store.upsert(app.copy(enabled = on))
                        refreshAll()
                    },
                    onToggleBackground = { app, on ->
                        store.upsert(app.copy(allowBackground = on))
                        refreshAll()
                    },
                    onToggleAutoStart = { app, on ->
                        store.upsert(app.copy(allowAutoStart = on))
                        refreshAll()
                    },
                    onRemove = { app ->
                        store.remove(app.packageName)
                        refreshAll()
                    },
                    onAdd = { showPicker = true }
                )

                ScreenTab.ALL -> AllAppsList(
                    modifier = Modifier.weight(1f),
                    apps = if (query.isBlank()) installed
                    else installed.filter {
                        it.label.contains(query, true) || it.packageName.contains(query, true)
                    },
                    managedPackages = managed.map { it.packageName }.toSet(),
                    query = query,
                    onQueryChange = { query = it },
                    includeSystem = includeSystem,
                    onIncludeSystemChange = { inc ->
                        includeSystem = inc
                        scope.launch {
                            val list = withContext(Dispatchers.IO) {
                                repo.loadAll(inc)
                            }
                            installed.clear()
                            installed.addAll(list)
                        }
                    },
                    onAdd = { entry ->
                        store.upsert(
                            ManagedApp(
                                packageName = entry.packageName,
                                appName = entry.label
                            )
                        )
                        refreshAll()
                    }
                )

                ScreenTab.LOG -> LogList(
                    modifier = Modifier.weight(1f),
                    logs = logs,
                    onClear = {
                        store.clearLog()
                        logs.clear()
                    }
                )
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            entries = installed,
            managedPackages = managed.map { it.packageName }.toSet(),
            onDismiss = { showPicker = false },
            onPick = { entry ->
                store.upsert(ManagedApp(packageName = entry.packageName, appName = entry.label))
                refreshAll()
                showPicker = false
            }
        )
    }
}

@Composable
private fun StatusCard(
    shizukuBinder: Boolean,
    shizukuReady: Boolean,
    serviceOn: Boolean,
    managedCount: Int,
    onRequestPermission: () -> Unit,
    onToggleService: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !shizukuBinder -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                shizukuReady -> Color(0xFF30A46C).copy(alpha = 0.15f)
                                else -> Color(0xFFF5A623).copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            !shizukuBinder -> Icons.Filled.Error
                            shizukuReady -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.Shield
                        },
                        contentDescription = null,
                        tint = when {
                            !shizukuBinder -> MaterialTheme.colorScheme.error
                            shizukuReady -> Color(0xFF30A46C)
                            else -> Color(0xFFF5A623)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !shizukuBinder -> "Shizuku 未运行"
                            shizukuReady -> "Shizuku 已授权"
                            else -> "Shizuku 待授权"
                        },
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = when {
                            !shizukuBinder -> "请先启动 Shizuku 应用"
                            shizukuReady -> "可执行系统级强制停止"
                            else -> "点击右侧按钮授权"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (shizukuBinder && !shizukuReady) {
                    TextButton(onClick = onRequestPermission) {
                        Text("授权")
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("守护开关", fontWeight = FontWeight.Medium)
                    Text(
                        if (serviceOn) "运行中 · 受管 $managedCount 个应用"
                        else "已停止",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = serviceOn,
                    onCheckedChange = onToggleService,
                    enabled = shizukuReady
                )
            }
        }
    }
}

@Composable
private fun ManagedList(
    modifier: Modifier = Modifier,
    apps: List<ManagedApp>,
    onToggleEnabled: (ManagedApp, Boolean) -> Unit,
    onToggleBackground: (ManagedApp, Boolean) -> Unit,
    onToggleAutoStart: (ManagedApp, Boolean) -> Unit,
    onRemove: (ManagedApp) -> Unit,
    onAdd: () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (apps.isEmpty()) {
            EmptyHint("还没有限制任何应用\n点击右下角 + 添加")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(apps, key = { it.packageName }) { app ->
                    ManagedRow(
                        app = app,
                        onToggleEnabled = { onToggleEnabled(app, it) },
                        onToggleBackground = { onToggleBackground(app, it) },
                        onToggleAutoStart = { onToggleAutoStart(app, it) },
                        onRemove = { onRemove(app) }
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加")
        }
    }
}

@Composable
private fun ManagedRow(
    app: ManagedApp,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleBackground: (Boolean) -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.appName, fontWeight = FontWeight.Medium)
                    Text(
                        app.packageName,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Switch(checked = app.enabled, onCheckedChange = onToggleEnabled)
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "移除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OptionChip(
                    label = "允许自启动",
                    active = app.allowAutoStart,
                    onClick = { onToggleAutoStart(!app.allowAutoStart) }
                )
                OptionChip(
                    label = "允许后台",
                    active = app.allowBackground,
                    onClick = { onToggleBackground(!app.allowBackground) }
                )
            }
        }
    }
}

@Composable
private fun OptionChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun AllAppsList(
    modifier: Modifier = Modifier,
    apps: List<AppEntry>,
    managedPackages: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    includeSystem: Boolean,
    onIncludeSystemChange: (Boolean) -> Unit,
    onAdd: (AppEntry) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            placeholder = { Text("搜索应用", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("显示系统应用", fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = includeSystem,
                onCheckedChange = onIncludeSystemChange,
                modifier = Modifier.height(28.dp)
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { entry ->
                val added = entry.packageName in managedPackages
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !added) { onAdd(entry) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.label, fontSize = 14.sp)
                        Text(
                            entry.packageName,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    if (added) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF30A46C),
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}

@Composable
private fun LogList(
    modifier: Modifier = Modifier,
    logs: List<String>,
    onClear: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "共 ${logs.size} 条记录",
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            TextButton(onClick = onClear) { Text("清空") }
        }
        if (logs.isEmpty()) {
            EmptyHint("暂无记录")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs) { line ->
                    Text(
                        text = line,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPickerDialog(
    entries: List<AppEntry>,
    managedPackages: Set<String>,
    onDismiss: () -> Unit,
    onPick: (AppEntry) -> Unit
) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(q, entries) {
        if (q.isBlank()) entries
        else entries.filter {
            it.label.contains(q, true) || it.packageName.contains(q, true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择要限制的应用") },
        text = {
            Column(modifier = Modifier.height(420.dp)) {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.packageName }) { e ->
                        val added = e.packageName in managedPackages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !added) { onPick(e) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(e.label, fontSize = 13.sp)
                                Text(
                                    e.packageName,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            if (added) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF30A46C),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}