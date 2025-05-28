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
import androidx.compose.material3.ExperimentalMaterial3Api
// import androidx.compose.material3.Icon 
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
// import androidx.compose.material3.TopAppBarDefaults 

// Icons (placeholders used, but real imports would be here)
// import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.filled.Close
// import androidx.compose.material.icons.filled.Search

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
import java.time.format.DateTimeFormatter 
import java.time.format.FormatStyle 
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


// displayValue function (as previously implemented)
fun displayValue(node: PlistNode): String {
    return when (node.type) {
        NodeType.STRING,
        NodeType.INTEGER,
        NodeType.REAL,
        NodeType.BOOLEAN -> node.value?.toString() ?: "null"
        NodeType.DATE -> {
            (node.value as? ZonedDateTime)?.format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            ) ?: node.value?.toString() ?: "Invalid Date"
        }
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

    // val SearchIcon = object { val name = "Search" } // Placeholder not used as direct Text is used
    // val CloseIcon = object { val name = "Close" } // Placeholder not used as direct Text is used

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
                                    Text("X") 
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
                             Text("S") 
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val rootNode by viewModel.nodes.observeAsState() 
        val expandedPaths by viewModel.expandedNodePaths.collectAsState() // Collect StateFlow

        if (rootNode == null) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No PList loaded or data is empty.")
            }
        } else {
            val visibleNodes = remember(rootNode, expandedPaths) {
                // Recompute the flat list when rootNode or expandedPaths change
                mutableListOf<DisplayableNode>().apply {
                    // Assuming rootNode itself might not have a path from a parent, generate its initial path
                    val initialPath = generateRootPath(rootNode!!) // rootNode is non-null here
                    buildVisibleNodesList(rootNode!!, initialPath, 0, expandedPaths, this)
                }
            }

            LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                items(visibleNodes, key = { it.path.hashCode() }) { displayableNode -> 
                    PlistNodeItem(
                        node = displayableNode.node,
                        depth = displayableNode.depth,
                        viewModel = viewModel, // For search query access later
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
    viewModel: PlistViewModel, // ViewModel is now used for query
    path: List<Any>,
    isExpanded: Boolean,
    onToggleNode: (List<Any>) -> Unit
) {
    val queryRegex by viewModel.query.collectAsState() // Collect search query as state

    // Determine if the node matches the search query
    val isMatch = remember(node, queryRegex) { // Re-calculate when node or query changes
        queryRegex?.let { regex ->
            val keyMatch = node.key?.let { regex.containsMatchIn(it) } ?: false
            // displayValue can be computationally intensive if called for every node, every time.
            // However, for highlighting, it's needed.
            // Consider optimizing if performance issues arise (e.g., pre-calculating displayValue for search).
            val valueMatch = regex.containsMatchIn(displayValue(node))
            keyMatch || valueMatch
        } ?: false // No query means no match
    }

    val backgroundColor = if (isMatch) Color.Yellow.copy(alpha = 0.3f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor) // Apply background color for highlighting
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
            // ... (Toggle icon, NodeType icon, Key-Value Text as before)
            if (node.children.isNotEmpty()) {
                Text(text = if (isExpanded) "v " else "> ", modifier = Modifier.width(16.dp))
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                text = when(node.type) { /* ... NodeType icons ... */ 
                    NodeType.DICTIONARY -> "{}"
                    NodeType.ARRAY -> "[]"
                    NodeType.STRING -> "S"
                    NodeType.INTEGER -> "I"
                    NodeType.REAL -> "R"
                    NodeType.BOOLEAN -> "B"
                    NodeType.DATE -> "D"
                    NodeType.DATA -> "d"
                    NodeType.ARCHIVE -> "A"
                },
                modifier = Modifier.padding(end = 8.dp)
            )
            val displayKey = node.key ?: (if (depth == 0 && path.firstOrNull() == "_root_") "Root" else path.lastOrNull()?.toString() ?: "")
            Text(text = "$displayKey: ${displayValue(node)}")
        }
        // No direct recursive calls for children here (handled by LazyColumn)
    }
}
