package com.example.plistviewer.ui.screens

// Base Compose imports
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Layout components
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer // Should be androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height // Should be androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width // Should be androidx.compose.foundation.layout.width

// LazyColumn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // For LazyColumn items, if we were passing a list

// LiveData observation
import androidx.compose.runtime.livedata.observeAsState // For observing LiveData

// Material 3 components:
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
// import androidx.compose.material3.TopAppBarDefaults // If needed for scroll behaviors

// Icons (placeholders used, but real imports would be here)
// import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.filled.Close
// import androidx.compose.material.icons.filled.Search

// Platform specific (though not heavily used in this step's code directly)
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
// import androidx.compose.ui.text.input.ImeAction

// ViewModel and model imports (ensure these are present)
import com.example.plistviewer.data.model.PlistNode
import com.example.plistviewer.data.model.NodeType
import com.example.plistviewer.ui.viewmodel.PlistViewModel
import com.google.protobuf.UnknownFieldSet // For displayValue
import java.time.ZonedDateTime // For displayValue
import java.time.format.DateTimeFormatter // For displayValue
import java.time.format.FormatStyle // For displayValue
import java.util.regex.PatternSyntaxException // For TopAppBar search


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
                is String -> { // Our placeholder for images
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlistScreen(viewModel: PlistViewModel) {
    var showSearchView by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // val keyboardController = LocalSoftwareKeyboardController.current // Keep if used
    // val focusManager = LocalFocusManager.current // Keep if used

    // Icon Placeholders are not directly used in this version as Text("S") / Text("X") is used
    // val SearchIcon = object { val name = "Search" }
    // val CloseIcon = object { val name = "Close" }

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
                                    // Using a regex that matches nothing or an empty regex
                                    viewModel.search("a^".toRegex()) // Or "".toRegex() if that's preferred for no match
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search (Regex)...") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.search("".toRegex()) // Clear search
                                }) {
                                    Text("X") // Placeholder for Close Icon
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
                             Text("S") // Placeholder for Search Icon
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val rootNode by viewModel.nodes.observeAsState() // Observe LiveData

        if (rootNode == null) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No PList loaded or data is empty.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                item { // Display the root node
                    // Ensure rootNode is not null before calling PlistNodeItem
                    rootNode?.let {
                        PlistNodeItem(node = it, depth = 0, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun PlistNodeItem(node: PlistNode, depth: Int, viewModel: PlistViewModel) {
    // TODO: Implement search highlighting and expand/collapse state later.
    // val query by viewModel.query.collectAsState() // For search term access (needs kotlinx.coroutines.flow.collectAsState)
    // val isMatch = query?.let { node.key?.contains(it, ignoreCase = true) == true || displayValue(node).contains(it, ignoreCase = true) } ?: false
    // val nodeColor = if (isMatch) Color.Yellow else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp) // Indentation
            // .background(nodeColor) // For search highlighting (later)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Placeholder for Icon (based on NodeType)
            Text(
                text = when(node.type) { // Simple text icons
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
            Text(text = "${node.key ?: (if (depth==0) "Root" else "")}: ${displayValue(node)}")
        }

        // Recursively display children if any
        if (node.children.isNotEmpty()) {
            // This makes PlistNodeItem manage its own children's display.
            // For true laziness with LazyColumn for very deep trees, a flattened list approach is better.
            // But for a "basic" implementation as requested, this is simpler.
            node.children.forEach { childNode ->
                PlistNodeItem(node = childNode, depth = depth + 1, viewModel = viewModel)
            }
        }
    }
}
