package com.example.plistviewer.data.model

import java.util.Date // Required for NSDate mapping initially
// import android.graphics.Bitmap // For DATA NodeType, if handling directly in model
// import com.google.protobuf.UnknownFieldSet // For DATA NodeType, if handling directly in model

enum class NodeType {
    DICTIONARY,
    ARRAY,
    STRING,
    INTEGER,
    REAL,
    BOOLEAN,
    DATE,
    DATA,
    ARCHIVE
}

data class PlistNode(
    val key: String?,
    val type: NodeType,
    val value: Any?, // Can be primitive, Date, ByteArray, Bitmap, UnknownFieldSet, or null
    val children: List<PlistNode> = emptyList()
)
