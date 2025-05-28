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
import java.time.ZonedDateTime 
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.example.plistviewer.data.model.NodeType 
import com.google.protobuf.UnknownFieldSet 


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

    /**
     * Clears the current error state.
     * This is typically called by the UI after an error has been displayed.
     */
    fun clearError() {
        errorState.value = null // Called from UI, so .value is fine.
    }

    // --- Path Generation Helpers (internal to ViewModel for this step) ---
    private fun generateNodePathInternal(parentPath: List<Any>, node: PlistNode, fallbackIndex: Int): List<Any> {
        val pathSegment = node.key ?: fallbackIndex
        return parentPath + pathSegment
    }

    private fun generateRootPathInternal(node: PlistNode): List<Any> {
        return listOf(node.key ?: "_root_")
    }
    
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
            else -> "" 
        }
    }


    fun loadPlist(uri: Uri) {
        viewModelScope.launch {
            try {
                // SIMULATION for InputStream (as before)
                val dummyData = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\"><plist version=\"1.0\"><dict><key>Test</key><string>Value</string></dict></plist>"
                val inputStream: InputStream = dummyData.byteInputStream()

                inputStream.use { stream ->
                    val parser: PlistParser = BinaryParser()
                    val parsedNode = withContext(Dispatchers.IO) { 
                        parser.parse(stream) 
                    }
                    nodes.postValue(parsedNode) // Use postValue for LiveData from background thread
                    errorState.postValue(null)  // Use postValue for LiveData from background thread
                    _expandedNodePaths.value = emptySet() // StateFlow .value is thread-safe
                }
            } catch (e: Exception) {
                errorState.postValue("Error parsing plist: ${e.localizedMessage ?: e.javaClass.simpleName}") // Use postValue
            }
        }
    }

    fun search(regex: Regex?) {
        query.value = regex // StateFlow .value is thread-safe
        if (regex == null || regex.pattern.isEmpty()) {
            // Current plan: do nothing with expansions on clear.
        } else {
            nodes.value?.let { rootNode -> // .value is fine for LiveData if only reading on main thread, or if sure it's set before this.
                                        // For safety, could launch another coroutine or ensure rootNode is accessed appropriately.
                                        // Given search is likely called from UI (main thread), nodes.value should be fine.
                val pathsToExpand = mutableSetOf<List<Any>>()
                findAllMatchingPaths(
                    currentNode = rootNode,
                    currentPath = generateRootPathInternal(rootNode),
                    regex = regex,
                    pathsToExpand = pathsToExpand
                )
                if (pathsToExpand.isNotEmpty()) {
                    _expandedNodePaths.update { it + pathsToExpand } // StateFlow .update is thread-safe
                }
            }
        }
    }

    /**
     * Recursively finds all paths that lead to nodes matching the regex.
     * If a node matches, its path and all its ancestor paths are added to pathsToExpand.
     * If a node's child matches, the node's path (as an ancestor) is also added.
     */
    private fun findAllMatchingPaths(
        currentNode: PlistNode,
        currentPath: List<Any>, // Full path to currentNode
        regex: Regex,
        pathsToExpand: MutableSet<List<Any>>
    ): Boolean { // Returns true if this node or any of its children matched
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
            // If this node itself matched, or one of its children matched,
            // then this node's path (currentPath) needs to be expanded to show the match or lead to it.
            pathsToExpand.add(currentPath)

            // Also, all ancestors of this currentPath must be expanded.
            // Example: if currentPath = [root, child1, item0, leafKey] matches,
            // we need to ensure [root], [root, child1], and [root, child1, item0] are also expanded.
            // The root itself has no parents to add.
            for (i in 1 until currentPath.size) { 
                pathsToExpand.add(currentPath.subList(0, i))
            }
            return true // Signal upwards that a match was found in this subtree
        }
        return false // No match in this node or its children
    }
}
