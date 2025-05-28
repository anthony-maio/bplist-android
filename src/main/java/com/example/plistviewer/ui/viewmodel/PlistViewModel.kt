package com.example.plistviewer.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.plistviewer.data.model.PlistNode 
import com.example.plistviewer.data.parser.BinaryParser
import com.example.plistviewer.data.parser.PlistParser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.ZonedDateTime // For a potential displayValue-like function
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.example.plistviewer.data.model.NodeType // For displayValue-like function
import com.google.protobuf.UnknownFieldSet // For displayValue-like function


class PlistViewModel(application: Application) : AndroidViewModel(application) {
    val nodes = MutableLiveData<PlistNode>()
    val query = MutableStateFlow<Regex?>(null)
    val errorState = MutableLiveData<String?>()

    private val _expandedNodePaths = MutableStateFlow<Set<List<Any>>>(emptySet())
    val expandedNodePaths: StateFlow<Set<List<Any>>> = _expandedNodePaths.asStateFlow()

    fun toggleNodeExpansion(path: List<Any>) {
        _expandedNodePaths.update { currentPaths ->
            if (currentPaths.contains(path)) currentPaths - path else currentPaths + path
        }
    }

    // --- Path Generation Helpers (internal to ViewModel for this step) ---
    private fun generateNodePathInternal(parentPath: List<Any>, node: PlistNode, fallbackIndex: Int): List<Any> {
        val pathSegment = node.key ?: fallbackIndex
        return parentPath + pathSegment
    }

    private fun generateRootPathInternal(node: PlistNode): List<Any> {
        return listOf(node.key ?: "_root_")
    }
    // --- End Path Generation Helpers ---

    // --- displayValue simplified version for ViewModel matching (internal) ---
    // NOTE: This is a simplified version. Ideally, the exact displayValue logic from UI
    // or a shared utility should be used if matching against formatted values.
    private fun getSearchableStringForNode(node: PlistNode): String {
        return when (node.type) {
            NodeType.STRING,
            NodeType.INTEGER,
            NodeType.REAL,
            NodeType.BOOLEAN -> node.value?.toString() ?: ""
            NodeType.DATE -> (node.value as? ZonedDateTime)?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)) ?: ""
            NodeType.DATA -> {
                when (node.value) {
                    is String -> if (node.value.startsWith("[Image:")) "[Image]" else node.value
                    is ByteArray -> "[Data: ${node.value.size} bytes]"
                    is UnknownFieldSet -> "[Data: ${node.value.serializedSize} bytes (Protobuf)]"
                    else -> node.value?.toString() ?: ""
                }
            }
            else -> "" // Dictionary, Array, Archive values themselves are not directly searched by this string
        }
    }
    // --- End displayValue simplified version ---


    fun loadPlist(uri: Uri) {
        viewModelScope.launch {
            try {
                val dummyData = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\"><plist version=\"1.0\"><dict><key>Test</key><string>Value</string></dict></plist>"
                val inputStream: InputStream = dummyData.byteInputStream()
                inputStream.use { stream ->
                    val parser: PlistParser = BinaryParser()
                    val parsedNode = withContext(Dispatchers.IO) { parser.parse(stream) }
                    nodes.postValue(parsedNode)
                    errorState.postValue(null)
                    _expandedNodePaths.value = emptySet()
                }
            } catch (e: Exception) {
                errorState.postValue("Error parsing plist: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    fun search(regex: Regex?) {
        query.value = regex
        if (regex == null || regex.pattern.isEmpty()) {
            // Current plan: do nothing with expansions on clear.
            // If clearing expansions was desired: _expandedNodePaths.value = emptySet()
        } else {
            nodes.value?.let { rootNode ->
                val pathsToExpand = mutableSetOf<List<Any>>()
                findAllMatchingPaths(
                    currentNode = rootNode,
                    currentPath = generateRootPathInternal(rootNode), // Initial path for the root
                    regex = regex,
                    pathsToExpand = pathsToExpand
                )
                if (pathsToExpand.isNotEmpty()) {
                    _expandedNodePaths.update { it + pathsToExpand }
                }
                // If no matches, current behavior is to not change expansion state.
                // Alternative: _expandedNodePaths.value = emptySet() // to collapse all on no match
            }
        }
    }

    private fun findAllMatchingPaths(
        currentNode: PlistNode,
        currentPath: List<Any>, // Full path to currentNode
        regex: Regex,
        pathsToExpand: MutableSet<List<Any>>
        // ancestorPath parameter removed as it's not strictly needed; parent paths derived from currentPath
    ): Boolean { // Return true if this node or any of its children matched
        var selfMatched = false
        if (currentNode.key?.let { regex.containsMatchIn(it) } == true) {
            selfMatched = true
        }
        if (!selfMatched && regex.containsMatchIn(getSearchableStringForNode(currentNode))) {
            selfMatched = true
        }

        var childMatched = false
        currentNode.children.forEachIndexed { index, childNode ->
            val childPath = generateNodePathInternal(currentPath, childNode, index)
            if (findAllMatchingPaths(childNode, childPath, regex, pathsToExpand)) {
                childMatched = true
            }
        }

        if (selfMatched || childMatched) {
            // If this node matched, or any of its children matched,
            // then all parent paths leading to this node (including this node's path if selfMatched)
            // should be expanded.
            // However, if only a child matched, this current node's path (currentPath) needs to be added
            // to ensure the path TO the child is open.
            pathsToExpand.add(currentPath) // Add path of current node if it or its descendants match

            // Add all ancestor paths of the current node.
            // This is slightly redundant if called for every node up the chain,
            // but ensures completeness. A more optimized approach might collect paths
            // and then add ancestors once at the end.
            // For currentPath = [root, child1, item0, leafKey]
            // We need to add: [root], [root, child1], [root, child1, item0]
            // The loop for (i in 1 until currentPath.size) in the prompt was to add parent paths.
            // Let's refine this: if selfMatched, add all its ancestors. If childMatched, this node (currentPath)
            // is an ancestor to that child, so it's added.
            
            // Add all parent paths of currentPath, ensuring they are expanded.
            // currentPath is the path to the current node. If it or its children matched,
            // then all paths leading to it must be expanded.
            for (i in 1 until currentPath.size) { 
                pathsToExpand.add(currentPath.subList(0, i))
            }
            return true // Signal upwards that a match was found in this subtree
        }
        return false // No match in this node or its children
    }
}
