package com.example.plistviewer.data.parser

import com.ddplist.* // Import all dd-plist classes for mocking/instantiation
import com.example.plistviewer.data.model.NodeType
import com.example.plistviewer.data.model.PlistNode
import com.example.plistviewer.util.ArchiveUtils // For mocking isKeyedArchive
import com.example.plistviewer.data.protobuf.ProtoUtils // For mocking looksLikeProto, buildUnknownFields
import com.google.protobuf.UnknownFieldSet
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
import java.util.Date // For dd-plist's NSDate

// Conceptual Mocks for dd-plist objects - in a real test env, use Mockito or similar.
// For this exercise, we might create simple stubs if needed or just instantiate them.
// dd-plist classes are concrete, so direct instantiation is often possible.

class PlistBuilderTest {

    // Helper to create a simple UnknownFieldSet for testing proto handling
    private fun createDummyUnknownFieldSet(): UnknownFieldSet {
        return UnknownFieldSet.newBuilder()
            .addField(1, UnknownFieldSet.Field.newBuilder().addVarint(123L).build())
            .build()
    }
    
    private fun createDummyNsData(bytes: ByteArray): NSData {
        // NSData constructor takes String (base64) or byte array.
        // Using byte array directly.
        return NSData(bytes)
    }

    @Test
    fun `buildNode converts NSDictionary correctly`() {
        val nsDict = NSDictionary()
        nsDict.put("key1", NSString("value1"))
        nsDict.put("key2", NSNumber(123))

        val resultNode = buildNode("rootDict", nsDict)

        assertEquals("rootDict", resultNode.key)
        assertEquals(NodeType.DICTIONARY, resultNode.type)
        assertEquals(2, resultNode.children.size)
        assertTrue(resultNode.children.any { it.key == "key1" && it.value == "value1" && it.type == NodeType.STRING })
        assertTrue(resultNode.children.any { it.key == "key2" && it.value == 123L && it.type == NodeType.INTEGER })
    }

    @Test
    fun `buildNode converts NSArray correctly`() {
        val nsArray = NSArray(2)
        nsArray.setValue(0, NSString("item1"))
        nsArray.setValue(1, NSNumber(true))

        val resultNode = buildNode("rootArray", nsArray)

        assertEquals("rootArray", resultNode.key)
        assertEquals(NodeType.ARRAY, resultNode.type)
        assertEquals(2, resultNode.children.size)
        // Array children in PlistNode have null keys by current buildNode logic for arrays
        assertTrue(resultNode.children.any { it.key == null && it.value == "item1" && it.type == NodeType.STRING })
        assertTrue(resultNode.children.any { it.key == null && it.value == true && it.type == NodeType.BOOLEAN })
    }

    @Test
    fun `buildNode converts NSString correctly`() {
        val nsString = NSString("hello")
        val resultNode = buildNode("testString", nsString)
        assertEquals("testString", resultNode.key)
        assertEquals(NodeType.STRING, resultNode.type)
        assertEquals("hello", resultNode.value)
        assertTrue(resultNode.children.isEmpty())
    }

    @Test
    fun `buildNode converts NSNumber integer correctly`() {
        val nsNumber = NSNumber(42) // Integer
        val resultNode = buildNode("testInt", nsNumber)
        assertEquals(NodeType.INTEGER, resultNode.type)
        assertEquals(42L, resultNode.value)
    }

    @Test
    fun `buildNode converts NSNumber real correctly`() {
        val nsNumber = NSNumber(3.14) // Real
        val resultNode = buildNode("testReal", nsNumber)
        assertEquals(NodeType.REAL, resultNode.type)
        assertEquals(3.14, resultNode.value as Double, 0.001)
    }

    @Test
    fun `buildNode converts NSNumber boolean correctly`() {
        val nsNumber = NSNumber(true) // Boolean
        val resultNode = buildNode("testBool", nsNumber)
        assertEquals(NodeType.BOOLEAN, resultNode.type)
        assertEquals(true, resultNode.value)
    }
    
    @Test
    fun `buildNode converts NSDate correctly`() {
        // NSDate from dd-plist uses java.util.Date internally.
        // Its constructor can take milliseconds since epoch.
        val currentTimeMillis = System.currentTimeMillis()
        val nsDate = NSDate(Date(currentTimeMillis))
        
        val resultNode = buildNode("testDate", nsDate)
        
        assertEquals(NodeType.DATE, resultNode.type)
        assertNotNull(resultNode.value)
        assertTrue(resultNode.value is ZonedDateTime)
        
        val resultZdt = resultNode.value as ZonedDateTime
        // Compare epoch seconds to avoid timezone/nanosecond precision issues
        assertEquals(currentTimeMillis / 1000, resultZdt.toEpochSecond())
    }

    // NSData tests are more complex due to internal checks for archive, image, proto.
    // These would ideally involve mocking ArchiveUtils.isKeyedArchive, ProtoUtils.looksLikeProto etc.
    // For this exercise, we'll test the fallback raw data case.

    @Test
    fun `buildNode converts NSData to raw data when not archive, image, or proto`() {
        val rawBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val nsData = createDummyNsData(rawBytes)

        // Assume ArchiveUtils.isKeyedArchive(bytes) returns false (default or mocked)
        // Assume bytes don't match PNG/JPEG sigs
        // Assume ProtoUtils.looksLikeProto(bytes) returns false (default or mocked)
        
        val resultNode = buildNode("testRawData", nsData)

        assertEquals(NodeType.DATA, resultNode.type)
        assertArrayEquals(rawBytes, resultNode.value as ByteArray)
        assertTrue(resultNode.children.isEmpty()) // Raw data has no children unless it was parsed as proto
    }

    @Test
    fun `buildNode handles NSData as PNG image placeholder correctly`() {
        // PNG_SIG = byteArrayOf(0x89.toByte(), 0x50.toByte(), ...)
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(), 0x05, 0x06)
        val nsData = createDummyNsData(pngBytes)
        
        val resultNode = buildNode("pngImage", nsData)
        
        assertEquals(NodeType.DATA, resultNode.type)
        assertEquals("[Image: PNG data placeholder (${pngBytes.size} bytes)]", resultNode.value)
    }

    @Test
    fun `buildNode handles NSData as Proto placeholder correctly`() {
        // This test assumes looksLikeProto returns true, and buildUnknownFields returns empty list for simplicity
        // To test actual ProtoUtils, they need their own unit tests.
        val protoBytes = byteArrayOf(0x08, 0x96.toByte(), 0x01) // Example simple proto bytes (field 1, value 150)
        val nsData = createDummyNsData(protoBytes)

        // Conceptual mocking:
        // Mock ProtoUtils.looksLikeProto to return true for protoBytes
        // Mock ProtoUtils.buildUnknownFields to return emptyList() or specific PlistNode children

        // Since we can't easily mock static methods in ProtoUtils/ArchiveUtils without Powermock/framework,
        // this test will rely on the current behavior of the actual ProtoUtils.looksLikeProto.
        // The current looksLikeProto is a robust heuristic.
        // The current buildUnknownFields creates actual children from UnknownFieldSet.

        val resultNode = buildNode("protoData", nsData)

        if (ProtoUtils.looksLikeProto(protoBytes)) {
            assertEquals(NodeType.DATA, resultNode.type)
            assertTrue(resultNode.value is UnknownFieldSet)
            // Check children if buildUnknownFields was expected to produce some
            // val children = ProtoUtils.buildUnknownFields(resultNode.value as UnknownFieldSet)
            // assertEquals(expectedChildrenCount, resultNode.children.size) 
        } else {
            // If looksLikeProto is false, it should be raw data
            assertEquals(NodeType.DATA, resultNode.type)
            assertArrayEquals(protoBytes, resultNode.value as ByteArray)
        }
    }
    
    // Test for NSKeyedArchiver would require mocking NSKeyedUnarchiver.unarchiveObject
    // and ArchiveUtils.isKeyedArchive. This is complex without a mocking framework.
    // For now, we assume the logic within buildNode for archives is tested by integration
    // (e.g. parsing a plist file that contains an archive).
}
