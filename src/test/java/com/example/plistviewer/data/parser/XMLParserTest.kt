package com.example.plistviewer.data.parser

import com.example.plistviewer.data.model.NodeType
import kotlinx.coroutines.runBlocking // For running suspend function in test
import org.junit.Assert.*
import org.junit.Test

class XMLParserTest {

    // Using String concatenation to avoid issues with leading zeros in literals within the multiline string
    private val sampleXmlPlist = """
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    <plist version="1.0">
    <dict>
        <key>name</key>
        <string>John Doe</string>
        <key>age</key>
        <integer>30</integer>
        <key>isStudent</key>
        <false/>
        <key>address</key>
        <dict>
            <key>street</key>
            <string>123 Main St</string>
            <key>city</key>
            <string>Anytown</string>
        </dict>
        <key>courses</key>
        <array>
            <string>Math</string>
            <string>Science</string>
        </array>
        <key>timestamp</key>
    """ + // Concatenating to avoid Python parsing issues with "01"
    """
        <date>2023-01-15T10:30:00Z</date> 
    </dict>
    </plist>
    """.trimIndent()
    // Note: dd-plist parses date string "2023-01-15T10:30:00Z" into a java.util.Date

    @Test
    fun `parse valid XML plist string`() = runBlocking { // Use runBlocking for suspend fun
        val parser = XMLParser()
        val inputStream = sampleXmlPlist.byteInputStream()
        
        val rootNode = parser.parse(inputStream)

        assertNotNull("Root node should not be null", rootNode)
        // The root itself is the <dict> from the plist. buildNode gives it a null key by default.
        assertEquals(null, rootNode.key) 
        assertEquals(NodeType.DICTIONARY, rootNode.type)
        assertEquals(6, rootNode.children.size) // name, age, isStudent, address, courses, timestamp

        // Test a few children
        val nameNode = rootNode.children.find { it.key == "name" }
        assertNotNull(nameNode)
        assertEquals(NodeType.STRING, nameNode?.type)
        assertEquals("John Doe", nameNode?.value)

        val ageNode = rootNode.children.find { it.key == "age" }
        assertNotNull(ageNode)
        assertEquals(NodeType.INTEGER, ageNode?.type)
        assertEquals(30L, ageNode?.value) // NSNumber longValue

        val isStudentNode = rootNode.children.find { it.key == "isStudent" }
        assertNotNull(isStudentNode)
        assertEquals(NodeType.BOOLEAN, isStudentNode?.type)
        assertEquals(false, isStudentNode?.value)
        
        val addressNode = rootNode.children.find { it.key == "address" }
        assertNotNull(addressNode)
        assertEquals(NodeType.DICTIONARY, addressNode?.type)
        assertEquals(2, addressNode?.children?.size)
        
        val coursesNode = rootNode.children.find { it.key == "courses" }
        assertNotNull(coursesNode)
        assertEquals(NodeType.ARRAY, coursesNode?.type)
        assertEquals(2, coursesNode?.children?.size)
        assertEquals("Math", coursesNode?.children?.get(0)?.value)

        val timestampNode = rootNode.children.find { it.key == "timestamp" }
        assertNotNull(timestampNode)
        assertEquals(NodeType.DATE, timestampNode?.type) 
    }

    @Test(expected = com.ddplist.PropertyListFormatException::class)
    fun `parse invalid XML plist string throws exception`() = runBlocking {
        val parser = XMLParser()
        val invalidXml = "<dict><key>test</key><string>value</plist>" // Malformed
        val inputStream = invalidXml.byteInputStream()
        parser.parse(inputStream) // Should throw
    }
}
