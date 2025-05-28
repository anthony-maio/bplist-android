package com.example.plistviewer.data.parser

import com.ddplist.*
import com.example.plistviewer.data.model.NodeType
import com.example.plistviewer.data.model.PlistNode
import com.example.plistviewer.util.Constants.APPLE_EPOCH
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import com.example.plistviewer.util.ArchiveUtils // For isKeyedArchive
import com.ddplist.NSKeyedUnarchiver // For unarchiving
import com.example.plistviewer.util.Constants // For PNG_SIG, JPEG_SIG
// import android.graphics.Bitmap // Placeholder
// import android.graphics.BitmapFactory // Placeholder
import com.example.plistviewer.data.protobuf.ProtoUtils // For looksLikeProto, buildUnknownFields
import com.google.protobuf.UnknownFieldSet // For parsing

// Helper extension function for ByteArray.startsWith
fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (this.size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}

/**
 * Recursively builds a PlistNode tree from dd-plist's NSObject representation.
 */
fun buildNode(key: String?, obj: NSObject): PlistNode {
    return when (obj) {
        is NSDictionary -> {
            val children = obj.allKeys().mapNotNull { childKey ->
                obj.objectForKey(childKey)?.let { childObj ->
                    buildNode(childKey, childObj)
                }
            }
            PlistNode(key, NodeType.DICTIONARY, null, children)
        }
        is NSArray -> {
            val children = obj.array.mapIndexedNotNull { index, childObj ->
                // For arrays, the key is typically the index, but our PlistNode's 'key' is String?.
                // We can pass null or index.toString(). For now, passing null.
                childObj?.let { buildNode(null /* or index.toString() */, it) }
            }
            PlistNode(key, NodeType.ARRAY, null, children)
        }
        is NSString -> PlistNode(key, NodeType.STRING, obj.content, emptyList())
        is NSNumber -> {
            when (obj.type) {
                NSNumber.BOOLEAN -> PlistNode(key, NodeType.BOOLEAN, obj.boolValue(), emptyList())
                NSNumber.INTEGER -> PlistNode(key, NodeType.INTEGER, obj.longValue(), emptyList())
                NSNumber.REAL -> PlistNode(key, NodeType.REAL, obj.doubleValue(), emptyList())
                else -> {
                    // Fallback for other NSNumber types if any, treat as Integer or Real based on some criteria
                    // For now, defaulting to Long if type is not explicitly Boolean or Real
                    try {
                        PlistNode(key, NodeType.INTEGER, obj.longValue(), emptyList())
                    } catch (e: Exception) {
                        try {
                            PlistNode(key, NodeType.REAL, obj.doubleValue(), emptyList())
                        } catch (e2: Exception) {
                            // If it cannot be represented as Long or Double, store as string or handle error
                            PlistNode(key, NodeType.STRING, obj.toString(), emptyList())
                        }
                    }
                }
            }
        }
        is NSDate -> {
            // Convert NSDate to ZonedDateTime
            // NSDate.timeIntervalSinceReferenceDate is seconds from 01/01/2001 UTC.
            // APPLE_EPOCH is milliseconds from 1970-01-01 UTC to 2001-01-01 UTC.
            // The dd-plist NSDate.getDate() returns a java.util.Date object,
            // which stores time as milliseconds since the 1970-01-01 epoch.
            // No conversion using APPLE_EPOCH is needed here as dd-plist handles it.
            val instant = Instant.ofEpochMilli(obj.date.time) // obj.date is java.util.Date from dd-plist
            val zoned = instant.atZone(ZoneId.systemDefault())
            PlistNode(key, NodeType.DATE, zoned, emptyList())
        }
        // NSData handling will be added in subsequent steps (keyed archive, image, proto, raw)
        // For now, a placeholder for unhandled NSData or other types:
        is NSData -> {
            val bytes = obj.bytes
            if (ArchiveUtils.isKeyedArchive(bytes)) {
                try {
                    val unarchivedRoot = NSKeyedUnarchiver.unarchiveObject(bytes) as NSObject
                    // Recursively call buildNode with the unarchived object.
                    // The type is explicitly set to ARCHIVE.
                    return buildNode(key, unarchivedRoot).copy(type = NodeType.ARCHIVE)
                } catch (e: Exception) {
                    // Handle cases where unarchiving might fail even if header matches
                    // Log error, return as raw data or specific error node.
                    // For now, falling back to treating as raw data.
                    // Consider adding a specific error type or logging e.message
                    // TODO: Add proper error logging for failed unarchiving
                    return PlistNode(key, NodeType.DATA, bytes, emptyList()) // Fallback to raw data
                }
            }

            // Image Detection
            val imagePlaceholderValue: String? = when {
                bytes.startsWith(Constants.PNG_SIG) -> {
                    // In a real Android environment, you would decode to a Bitmap:
                    // val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    // return PlistNode(key, NodeType.DATA, bitmap, emptyList())
                    "[Image: PNG data placeholder (${bytes.size} bytes)]"
                }
                bytes.startsWith(Constants.JPEG_SIG) -> {
                    // In a real Android environment, you would decode to a Bitmap:
                    // val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    // return PlistNode(key, NodeType.DATA, bitmap, emptyList())
                    "[Image: JPEG data placeholder (${bytes.size} bytes)]"
                }
                else -> null
            }

            if (imagePlaceholderValue != null) {
                // Storing the placeholder string as the value.
                // In a real app, this would be a Bitmap object.
                return PlistNode(key, NodeType.DATA, imagePlaceholderValue /* or actual bytes/Bitmap */, emptyList())
            }

            // Protobuf Detection
            if (ProtoUtils.looksLikeProto(bytes)) {
                try {
                    val unknowns = UnknownFieldSet.parseFrom(bytes)
                    val protoChildren = ProtoUtils.buildUnknownFields(unknowns) // This returns List<PlistNode>
                    // The 'value' for a successfully parsed proto structure could be the UnknownFieldSet itself,
                    // or a summary string, or null if all info is in children.
                    // Spec: PlistNode(key, NodeType.DATA, unknowns, buildUnknownFields(unknowns))
                    // This means the UnknownFieldSet is the value, and children are also derived.
                    return PlistNode(key, NodeType.DATA, unknowns, protoChildren)
                } catch (e: Exception) {
                    // Handle cases where parsing might fail even if looksLikeProto was true.
                    // Log error, and fallback to raw data.
                    // TODO: Add proper error logging for failed protobuf parsing
                    // Fall through to raw data handling if proto parsing fails
                }
            }

            // Fallback: if neither image nor proto (nor archive), store raw ByteArray.
            PlistNode(key, NodeType.DATA, bytes, emptyList())
        }
        else -> {
            // Fallback for any other NSObject type not explicitly handled yet.
            // This could be UID or other less common plist types.
            // Store its string representation or a generic "unknown" node.
            PlistNode(key, NodeType.STRING, obj.toXMLPropertyList(), emptyList()) // Using toXMLPropertyList as a generic string value
        }
    }
}
