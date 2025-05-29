package com.example.plistviewer.data.protobuf

import com.example.plistviewer.data.model.NodeType
import com.google.protobuf.ByteString
import com.google.protobuf.UnknownFieldSet
import org.junit.Assert.*
import org.junit.Test

class ProtoUtilsTest {

    // --- looksLikeProto Tests ---

    @Test
    fun `looksLikeProto returns true for minimal valid protobuf`() {
        // Smallest valid field: field number 1, wire type 0 (Varint)
        // Tag = (1 << 3) | 0 = 8 (0x08)
        // Value = 1 (0x01)
        val minimalProto = byteArrayOf(0x08.toByte(), 0x01.toByte())
        assertTrue("Should identify minimal valid proto", ProtoUtils.looksLikeProto(minimalProto))
    }

    @Test
    fun `looksLikeProto returns true for more complex valid protobuf`() {
        // Field 1: Varint, value 150 (0x08, 0x96, 0x01)
        // Field 2: Length-delimited, length 2, value "hi" (0x12, 0x02, 0x68, 0x69)
        val complexProto = byteArrayOf(
            0x08.toByte(), 0x96.toByte(), 0x01.toByte(), // Field 1, Varint 150
            0x12.toByte(), 0x02.toByte(), 0x68.toByte(), 0x69.toByte() // Field 2, String "hi"
        )
        assertTrue("Should identify complex valid proto", ProtoUtils.looksLikeProto(complexProto))
    }

    @Test
    fun `looksLikeProto returns false for empty byte array`() {
        assertFalse("Should return false for empty array", ProtoUtils.looksLikeProto(byteArrayOf()))
    }

    @Test
    fun `looksLikeProto returns false for invalid tag (field number 0)`() {
        // Tag for field 0 is invalid. (0 << 3) | 0 = 0
        val invalidTagProto = byteArrayOf(0x00.toByte(), 0x01.toByte())
        assertFalse("Should return false for tag with field number 0", ProtoUtils.looksLikeProto(invalidTagProto))
    }
    
    @Test
    fun `looksLikeProto returns false for invalid wire type`() {
        // Field 1, wire type 7 (invalid)
        // Tag = (1 << 3) | 7 = 0x0F
        val invalidWireTypeProto = byteArrayOf(0x0F.toByte(), 0x01.toByte())
        assertFalse("Should return false for invalid wire type", ProtoUtils.looksLikeProto(invalidWireTypeProto))
    }

    @Test
    fun `looksLikeProto returns false for truncated length-delimited field`() {
        // Field 2, Length-delimited, claimed length 5, actual data 2 bytes "hi"
        val truncatedProto = byteArrayOf(0x12.toByte(), 0x05.toByte(), 0x68.toByte(), 0x69.toByte())
        assertFalse("Should return false for truncated length-delimited field", ProtoUtils.looksLikeProto(truncatedProto))
    }
    
    @Test
    fun `looksLikeProto returns false for random non-protobuf bytes`() {
        val nonProto = "This is not protobuf data".toByteArray(Charsets.UTF_8)
        assertFalse("Should return false for random non-protobuf bytes", ProtoUtils.looksLikeProto(nonProto))
    }

    // --- buildUnknownFields Tests ---

    @Test
    fun `buildUnknownFields handles empty UnknownFieldSet`() {
        val emptyUnknowns = UnknownFieldSet.newBuilder().build()
        val resultNodes = ProtoUtils.buildUnknownFields(emptyUnknowns)
        assertTrue("Result for empty UnknownFieldSet should be an empty list", resultNodes.isEmpty())
    }

    @Test
    fun `buildUnknownFields converts varint correctly`() {
        val fieldNumber = 1
        val varintValue = 150L
        val unknowns = UnknownFieldSet.newBuilder()
            .addField(fieldNumber, UnknownFieldSet.Field.newBuilder().addVarint(varintValue).build())
            .build()

        val resultNodes = ProtoUtils.buildUnknownFields(unknowns)
        assertEquals(1, resultNodes.size)
        val fieldNode = resultNodes.first()
        assertEquals("Field $fieldNumber", fieldNode.key)
        assertEquals(NodeType.DICTIONARY, fieldNode.type) // Parent node for the field
        assertEquals(1, fieldNode.children.size)
        
        val varintNode = fieldNode.children.first()
        assertEquals("VarInt[0]", varintNode.key)
        assertEquals(NodeType.INTEGER, varintNode.type)
        assertEquals(varintValue, varintNode.value)
    }

    @Test
    fun `buildUnknownFields converts length-delimited (string) correctly`() {
        val fieldNumber = 2
        val stringValue = "hello"
        val byteStringValue = ByteString.copyFromUtf8(stringValue)
        val unknowns = UnknownFieldSet.newBuilder()
            .addField(fieldNumber, UnknownFieldSet.Field.newBuilder().addLengthDelimited(byteStringValue).build())
            .build()

        val resultNodes = ProtoUtils.buildUnknownFields(unknowns)
        assertEquals(1, resultNodes.size)
        val fieldNode = resultNodes.first()
        assertEquals("Field $fieldNumber", fieldNode.key)
        assertEquals(1, fieldNode.children.size)

        val dataNode = fieldNode.children.first()
        assertEquals("LengthDelimited[0]", dataNode.key)
        assertEquals(NodeType.DATA, dataNode.type)
        // Current buildUnknownFields tries to interpret as string if not nested proto
        assertEquals(stringValue, dataNode.value) 
    }
    
    @Test
    fun `buildUnknownFields converts length-delimited (nested proto) correctly`() {
        val outerFieldNumber = 1
        val innerFieldNumber = 101
        val innerVarintValue = 42L

        // Create inner UnknownFieldSet bytes
        val innerUnknownsBuilder = UnknownFieldSet.newBuilder()
        innerUnknownsBuilder.addField(innerFieldNumber, UnknownFieldSet.Field.newBuilder().addVarint(innerVarintValue).build())
        val innerUnknownsBytes = innerUnknownsBuilder.build().toByteString()

        // Create outer UnknownFieldSet
        val outerUnknowns = UnknownFieldSet.newBuilder()
            .addField(outerFieldNumber, UnknownFieldSet.Field.newBuilder().addLengthDelimited(innerUnknownsBytes).build())
            .build()

        val resultNodes = ProtoUtils.buildUnknownFields(outerUnknowns)
        assertEquals(1, resultNodes.size) // Outer field node

        val outerFieldNode = resultNodes.first()
        assertEquals("Field $outerFieldNumber", outerFieldNode.key)
        assertEquals(1, outerFieldNode.children.size) // One length-delimited item

        val lengthDelimitedNode = outerFieldNode.children.first()
        assertEquals("LengthDelimited[0]", lengthDelimitedNode.key)
        assertEquals(NodeType.DATA, lengthDelimitedNode.type)
        assertEquals("[Nested Protobuf]", lengthDelimitedNode.value) // Placeholder for nested
        
        // Check children of the nested protobuf
        assertFalse("Nested protobuf should have children", lengthDelimitedNode.children.isEmpty())
        assertEquals(1, lengthDelimitedNode.children.size) // Inner field node

        val innerFieldNode = lengthDelimitedNode.children.first()
        assertEquals("Field $innerFieldNumber", innerFieldNode.key)
        assertEquals(1, innerFieldNode.children.size) // One varint item

        val innerVarintNode = innerFieldNode.children.first()
        assertEquals("VarInt[0]", innerVarintNode.key)
        assertEquals(NodeType.INTEGER, innerVarintNode.type)
        assertEquals(innerVarintValue, innerVarintNode.value)
    }
}
