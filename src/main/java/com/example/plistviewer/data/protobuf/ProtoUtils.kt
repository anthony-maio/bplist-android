package com.example.plistviewer.data.protobuf

import com.example.plistviewer.data.model.NodeType
import com.example.plistviewer.data.model.PlistNode
import com.google.protobuf.UnknownFieldSet
import com.google.protobuf.WireFormat
import com.google.protobuf.ByteString // For length-delimited data
import com.google.protobuf.CodedInputStream
import com.google.protobuf.InvalidProtocolBufferException

object ProtoUtils {

    fun looksLikeProto(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) {
            return false
        }

        val cis = CodedInputStream.newInstance(bytes)
        var tagsRead = 0
        try {
            while (!cis.isAtEnd) {
                val tag = cis.readTag()
                if (tag == 0) {
                    return tagsRead > 0 
                }

                val fieldNumber = WireFormat.getTagFieldNumber(tag)
                if (fieldNumber <= 0) {
                    return false 
                }

                val wireType = WireFormat.getTagWireType(tag)
                if (wireType !in listOf(
                        WireFormat.WIRETYPE_VARINT,
                        WireFormat.WIRETYPE_FIXED64,
                        WireFormat.WIRETYPE_LENGTH_DELIMITED,
                        WireFormat.WIRETYPE_START_GROUP, 
                        WireFormat.WIRETYPE_END_GROUP,   
                        WireFormat.WIRETYPE_FIXED32
                    )) {
                    return false 
                }
                if (!cis.skipField(tag)) {
                    return false
                }
                tagsRead++
            }
            return tagsRead > 0
        } catch (e: InvalidProtocolBufferException) {
            return false
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Converts an UnknownFieldSet from Protobuf into a list of PlistNode items.
     * Each entry in the UnknownFieldSet (fieldNumber, wireType, raw values) will be a child PlistNode.
     *
     * @param unknowns The UnknownFieldSet to convert.
     * @return A list of PlistNode representing the fields in the UnknownFieldSet.
     */
    fun buildUnknownFields(unknowns: UnknownFieldSet): List<PlistNode> {
        val children = mutableListOf<PlistNode>()
        unknowns.asMap().forEach { (fieldNumber, fieldData) ->
            val fieldSpecificChildren = mutableListOf<PlistNode>()

            fieldData.varintList.forEachIndexed { index, value ->
                fieldSpecificChildren.add(PlistNode("VarInt[$index]", NodeType.INTEGER, value, emptyList()))
            }
            fieldData.fixed32List.forEachIndexed { index, value ->
                fieldSpecificChildren.add(PlistNode("Fixed32[$index]", NodeType.STRING, "0x${Integer.toHexString(value)} (Int: $value)", emptyList()))
            }
            fieldData.fixed64List.forEachIndexed { index, value ->
                fieldSpecificChildren.add(PlistNode("Fixed64[$index]", NodeType.STRING, "0x${java.lang.Long.toHexString(value)} (Long: $value)", emptyList()))
            }
            fieldData.lengthDelimitedList.forEachIndexed { index, value ->
                val bytes = value.toByteArray()
                val nestedChildren = mutableListOf<PlistNode>()
                var valueRepresentation: Any = "[Data: ${bytes.size} bytes]" // Default
                
                if (looksLikeProto(bytes)) {
                    try {
                        val nestedUnknowns = UnknownFieldSet.parseFrom(bytes)
                        nestedChildren.addAll(buildUnknownFields(nestedUnknowns))
                        valueRepresentation = "[Nested Protobuf]" 
                    } catch (e: Exception) {
                         valueRepresentation = "[Data: ${bytes.size} bytes, hex: ${bytesToHex(bytes)}]"
                    }
                } else {
                    try {
                        val strValue = value.toStringUtf8()
                        if (strValue.any { it.code < 32 && it !in listOf('\n', '\r', '\t') }) {
                             valueRepresentation = "[Data: ${bytes.size} bytes, hex: ${bytesToHex(bytes)}]"
                        } else {
                             valueRepresentation = strValue
                        }
                    } catch (e: Exception) {
                        valueRepresentation = "[Data: ${bytes.size} bytes, hex: ${bytesToHex(bytes)}]"
                    }
                }
                fieldSpecificChildren.add(PlistNode("LengthDelimited[$index]", NodeType.DATA, valueRepresentation, nestedChildren))
            }
            fieldData.groupList.forEachIndexed { index, nestedUnknownFieldSet -> // Corrected variable name
                fieldSpecificChildren.add(PlistNode("Group[$index]", NodeType.ARCHIVE, "[Group Data]", buildUnknownFields(nestedUnknownFieldSet))) // Corrected variable name
            }

            children.add(PlistNode("Field $fieldNumber", NodeType.DICTIONARY, null, fieldSpecificChildren))
        }
        return children
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }
}
