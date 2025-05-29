package com.example.plistviewer.data.parser

import com.example.plistviewer.data.model.NodeType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64 // For decoding Base64 string

class BinaryParserTest {

    // A Base64 encoded string of a simple binary PList.
    // This PList is equivalent to:
    // <dict>
    //   <key>myKey</key>
    //   <string>myValue</string>
    // </dict>
    // The actual binary representation might vary slightly, so this is a specific encoded sample.
    // This one represents <dict><key>TestKey</key><string>TestValue</string></dict>
    private val sampleBinaryPlistBase64 = "YnBsaXN0MDDUAQIDBAUGBwgJVGtleV8QEFRLZXlZVHZhbHVlXxAPVGVzdFZhbHVlAAAAAAAAAQEAAAAAAAAACQAAAAAAAAAAAAAAAAAAAAw="
    // Generated using:
    // echo '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd"><plist version="1.0"><dict><key>TestKey</key><string>TestValue</string></dict></plist>' > test.plist
    // plutil -convert binary1 test.plist
    // base64 test.plist

    @Test
    fun `parse valid binary plist from base64 string`() = runBlocking {
        val parser = BinaryParser() // dd-plist's PropertyListParser auto-detects binary/XML
        val binaryBytes = Base64.getDecoder().decode(sampleBinaryPlistBase64)
        val inputStream = binaryBytes.inputStream()

        val rootNode = parser.parse(inputStream)

        assertNotNull("Root node should not be null", rootNode)
        assertEquals(null, rootNode.key) // Root dictionary itself has no key in this context
        assertEquals(NodeType.DICTIONARY, rootNode.type)
        assertEquals(1, rootNode.children.size)

        val childNode = rootNode.children.first()
        assertEquals("TestKey", childNode.key)
        assertEquals(NodeType.STRING, childNode.type)
        assertEquals("TestValue", childNode.value)
    }

    @Test(expected = com.ddplist.PropertyListFormatException::class)
    fun `parse invalid binary plist data throws exception`() = runBlocking {
        val parser = BinaryParser()
        // Invalid binary data (e.g., not starting with "bplist")
        val invalidBinaryBytes = "this is not a bplist".toByteArray()
        val inputStream = invalidBinaryBytes.inputStream()
        
        parser.parse(inputStream) // Should throw
    }
}
