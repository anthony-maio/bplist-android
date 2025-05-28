package com.example.plistviewer.data.parser

import com.ddplist.PropertyListParser
import com.example.plistviewer.data.model.PlistNode
import java.io.InputStream

/**
 * Parses XML Property List files.
 *
 * This parser specifically handles XML-formatted plists.
 * dd-plist's PropertyListParser.parse can auto-detect format,
 * but this class could be used if XML format is known beforehand or for specific error handling.
 */
class XMLParser : PlistParser {

    /**
     * Parses an XML property list from an input stream.
     *
     * @param input The InputStream containing the XML plist data.
     * @return The root PlistNode of the parsed structure.
     * @throws Exception if parsing fails.
     */
    override suspend fun parse(input: InputStream): PlistNode {
        // Ensure parsing happens on an IO dispatcher if called from a CoroutineScope
        // withContext(Dispatchers.IO) { /* ... parsing logic ... */ }
        // For now, direct call as per current structure.

        val rootObj = PropertyListParser.parse(input)
        // Assuming the root of a plist is typically a dictionary or an array,
        // the key for the absolute root node can be considered null or a default name.
        // The buildNode function expects a key.
        return buildNode(key = null /* Or a default root key like "root" */, obj = rootObj)
    }
}
