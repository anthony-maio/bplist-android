package com.example.plistviewer.data.parser

import com.example.plistviewer.data.model.PlistNode
import java.io.InputStream

interface PlistParser {
    suspend fun parse(input: InputStream): PlistNode
}
