package com.example.plistviewer.ui.screens

// Base Compose imports
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Layout components
import androidx.compose.foundation.clickable // For clickable modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer 
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
// import androidx.compose.foundation.layout.height 
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width 

// LazyColumn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Crucial for using list with LazyColumn

// LiveData & StateFlow observation
import androidx.compose.runtime.livedata.observeAsState // For observing LiveData
import androidx.compose.runtime.collectAsState // For collecting StateFlow

// Highlighting
import androidx.compose.ui.graphics.Color // For background color
import androidx.compose.foundation.background // For background modifier


// Material 3 components:
import androidx.compose.material3.AlertDialog // For error dialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon 
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton // For AlertDialog button
import androidx.compose.material3.TopAppBar
// import androidx.compose.material3.TopAppBarDefaults 

// Icons
import androidx.compose.material.icons.Icons // Standard icons
import androidx.compose.material.icons.filled.Close // For clearing search (TopAppBar)
import androidx.compose.material.icons.filled.Search // Search Icon (TopAppBar)
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Article // For String
import androidx.compose.material.icons.filled.CalendarToday // For Date
import androidx.compose.material.icons.filled.CheckBox // For Boolean
import androidx.compose.material.icons.filled.DataObject // For Dictionary
import androidx.compose.material.icons.filled.Description // For general data/string fallback
import androidx.compose.material.icons.filled.Image // For Image Data
import androidx.compose.material.icons.filled.List // For Array
import androidx.compose.material.icons.filled.Numbers // For Integer/Real
import androidx.compose.material.icons.filled.Archive // For Archive
import androidx.compose.material.icons.filled.Memory // For raw data / protobuf


// Platform specific
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
// import androidx.compose.ui.text.input.ImeAction

// ViewModel and model imports
import com.example.plistviewer.data.model.PlistNode 
import com.example.plistviewer.data.model.NodeType
import com.example.plistviewer.ui.viewmodel.PlistViewModel
import com.google.protobuf.UnknownFieldSet 
import java.time.ZonedDateTime 
// Removed: java.time.format.DateTimeFormatter (no longer used directly here)
// Removed: java.time.format.FormatStyle (no longer used directly here)
import com.example.plistviewer.util.DateUtils // Added import for DateUtils
import java.util.regex.PatternSyntaxException 


/**
 * Generates a unique path for a PlistNode.
 * A path is a list of keys (String) and indices (Int).
 */
fun generateNodePath(parentPath: List<Any>, node: PlistNode, fallbackIndex: Int): List<Any> {
    val pathSegment = node.key ?: fallbackIndex
    return parentPath + pathSegment
}

/**
 * Generates a root path for the initial node.
 */
fun generateRootPath(node: PlistNode): List<Any> {
    return listOf(node.key ?: "_root_")
}


fun displayValue(node: PlistNode): String {
    return when (node.type) {
        NodeType.STRING,
        NodeType.INTEGER,
        NodeType.REAL,
        NodeType.BOOLEAN -> node.value?.toString() ?: "null"
        NodeType.DATE -> DateUtils.formatZonedDateTimeForDisplay(node.value as? ZonedDateTime) // Updated
        NodeType.DATA -> {
            when (node.value) {
                is String -> { 
                    if (node.value.startsWith("[Image:")) "[Image]" else node.value
                }
                is ByteArray -> "[Data: ${node.value.size} bytes]"
                is UnknownFieldSet -> "[Data: ${node.value.serializedSize} bytes (Protobuf)]"
                else -> node.value?.toString() ?: "[Unknown Data]"
            }
        }
        NodeType.DICTIONARY -> "[Dictionary]"
        NodeType.ARRAY -> "[Array]"
        NodeType.ARCHIVE -> "[Archive]"
    }
}

data class DisplayableNode(
    val node: PlistNode,
    val path: List<Any>,
    val depth: Int
)

fun buildVisibleNodesList(
    currentNode: PlistNode,
    currentPath: List<Any>,
    currentDepth: Int,
    expandedPaths: Set<List<Any>>,
    visibleList: MutableList<DisplayableNode>
) {
    visibleList.add(DisplayableNode(currentNode, currentPath, currentDepth))

    if (expandedPaths.contains(currentPath) && currentNode.children.isNotEmpty()) {
        currentNode.children.forEachIndexed { index, childNode ->
            val childPath = generateNodePath(currentPath, childNode, index)
            buildVisibleNodesList(childNode, childPath, currentDepth + 1, expandedPaths, visibleList)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlistScreen(viewModel: PlistViewModel) {
    var showSearchView by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current 
    val focusManager = LocalFocusManager.current 

    val rootNode by viewModel.nodes.observeAsState()
    val expandedPaths by viewModel.expandedNodePaths.collectAsState()
    val errorMessage by viewModel.errorState.observeAsState() 

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() }, 
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchView) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                try {
                                    viewModel.search(it.toRegex(RegexOption.IGNORE_CASE))
                                } catch (e: PatternSyntaxException) {
                                    viewModel.search("a^".toRegex()) 
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search (Regex)...") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.search("".toRegex()) 
                                }) {
                                     Icon(Icons.Filled.Close, contentDescription = "Clear Search")
                                }
                            }
                        )
                    } else {
                        Text("Plist Viewer")
                    }
                },
                actions = {
                    if (!showSearchView) {
                        IconButton(onClick = { showSearchView = true }) {
                             Icon(Icons.Filled.Search, contentDescription = "Search Plist")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (rootNode == null && errorMessage == null) { 
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No PList loaded or data is empty.")
            }
        } else if (rootNode != null) { 
            val visibleNodes = remember(rootNode, expandedPaths) {
                mutableListOf<DisplayableNode>().apply {
                    val initialPath = generateRootPath(rootNode!!)
                    buildVisibleNodesList(rootNode!!, initialPath, 0, expandedPaths, this)
                }
            }

            LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                items(visibleNodes, key = { it.path.hashCode() }) { displayableNode -> 
                    PlistNodeItem(
                        node = displayableNode.node,
                        depth = displayableNode.depth,
                        viewModel = viewModel,
                        path = displayableNode.path,
                        isExpanded = expandedPaths.contains(displayableNode.path),
                        onToggleNode = { path -> viewModel.toggleNodeExpansion(path) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlistNodeItem(
    node: PlistNode,
    depth: Int,
    viewModel: PlistViewModel, 
    path: List<Any>,
    isExpanded: Boolean,
    onToggleNode: (List<Any>) -> Unit
) {
    val queryRegex by viewModel.query.collectAsState() 
    val isMatch = remember(node, queryRegex) { 
        queryRegex?.let { regex ->
            val keyMatch = node.key?.let { regex.containsMatchIn(it) } ?: false
            val valueMatch = regex.containsMatchIn(displayValue(node))
            keyMatch || valueMatch
        } ?: false 
    }
    val backgroundColor = if (isMatch) Color.Yellow.copy(alpha = 0.3f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor) 
            .padding(start = (depth * 16).dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = node.children.isNotEmpty(),
                    onClick = { onToggleNode(path) }
                )
                .padding(vertical = 4.dp)
        ) {
            if (node.children.isNotEmpty()) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ArrowDropDown else Icons.Filled.ArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.width(24.dp) 
                )
            } else {
                Spacer(modifier = Modifier.width(24.dp)) 
            }
            val typeIcon = when (node.type) {
                NodeType.DICTIONARY -> Icons.Filled.DataObject
                NodeType.ARRAY -> Icons.Filled.List
                NodeType.STRING -> Icons.Filled.Article 
                NodeType.INTEGER, NodeType.REAL -> Icons.Filled.Numbers
                NodeType.BOOLEAN -> Icons.Filled.CheckBox
                NodeType.DATE -> Icons.Filled.CalendarToday
                NodeType.DATA -> {
                    when (node.value) {
                        is String -> { 
                            if (node.value.startsWith("[Image:")) Icons.Filled.Image else Icons.Filled.Article
                        }
                        is UnknownFieldSet -> Icons.Filled.Memory 
                        else -> Icons.Filled.Description 
                    }
                }
                NodeType.ARCHIVE -> Icons.Filled.Archive
            }
            Icon(
                imageVector = typeIcon,
                contentDescription = node.type.name, 
                modifier = Modifier.padding(horizontal = 8.dp) 
            )
            
            val displayKey = node.key ?: (if (depth == 0 && path.firstOrNull() == "_root_") "Root" else path.lastOrNull()?.toString() ?: "")
            Text(text = "$displayKey: ${displayValue(node)}")
        }
    }
}
