package com.example.plistviewer.data.parser

import com.ddplist.PropertyListParser
import com.example.plistviewer.data.model.PlistNode
import java.io.InputStream

/**
 * Parses Binary Property List files.
 *
 * This parser is designated for binary-formatted plists.
 * dd-plist's PropertyListParser.parse can auto-detect format,
 * but this class could be used if binary format is known beforehand or for specific error handling.
 */
class BinaryParser : PlistParser {

    /**
     * Parses a binary property list from an input stream.
     *
     * @param input The InputStream containing the binary plist data.
     * @return The root PlistNode of the parsed structure.
     * @throws Exception if parsing fails.
     */
    override suspend fun parse(input: InputStream): PlistNode {
        // Ensure parsing happens on an IO dispatcher if called from a CoroutineScope
        // withContext(Dispatchers.IO) { /* ... parsing logic ... */ }
        // For now, direct call.

        val rootObj = PropertyListParser.parse(input)
        // The key for the absolute root node can be considered null or a default name.
        return buildNode(key = null /* Or a default root key like "root" */, obj = rootObj)
    }
}
