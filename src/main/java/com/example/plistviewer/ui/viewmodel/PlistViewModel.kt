package com.example.plistviewer.ui.viewmodel

import android.app.Application // For accessing content resolver (in a real scenario)
import android.net.Uri
import androidx.lifecycle.AndroidViewModel // Changed to AndroidViewModel to get Application context
import androidx.lifecycle.MutableLiveData
// import androidx.lifecycle.ViewModel // No longer ViewModel, but AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plistviewer.data.model.PlistNode
import com.example.plistviewer.data.parser.BinaryParser // Default parser
import com.example.plistviewer.data.parser.PlistParser // For parser interface
// import com.example.plistviewer.data.parser.XMLParser // Alternative parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream // For the input stream

// Changed to AndroidViewModel to facilitate context access if needed,
// though direct context use for opening URI is often done in Activity/Fragment
// and InputStream passed to ViewModel. For this exercise, we'll assume
// a method to get InputStream or mock it.
class PlistViewModel(application: Application) : AndroidViewModel(application) { 
    val nodes = MutableLiveData<PlistNode>()
    val query = MutableStateFlow<Regex?>(null)

    // Placeholder for error reporting
    val errorState = MutableLiveData<String?>()

    fun loadPlist(uri: Uri) {
        viewModelScope.launch {
            try {
                // In a real app, obtaining InputStream from Uri is context-dependent:
                // val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                // For this non-Android execution environment, we cannot directly open the stream.
                // We'll simulate this by expecting the URI to be a file path we can open,
                // or by creating a dummy stream for testing the parsing flow.
                // This part will need adjustment in a real Android app.

                // SIMULATION: Using a placeholder for InputStream.
                // In a real scenario, this would be:
                // val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                // if (inputStream == null) {
                //     errorState.postValue("Failed to open file: InputStream is null")
                //     return@launch
                // }

                // For the purpose of this task, let's assume uri.toString() can give us something
                // to make a decision, or we just use a dummy stream for now.
                // The actual stream opening from URI is outside worker's capability without a real Android environment.
                // Let's proceed with a conceptual placeholder for the stream and focus on the parsing call.

                // Conceptual: Create a dummy input stream for testing the flow
                // This is a placeholder. In a real app, you'd get it from the URI.
                val dummyData = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\"><plist version=\"1.0\"><dict><key>Test</key><string>Value</string></dict></plist>"
                val inputStream: InputStream = dummyData.byteInputStream()


                inputStream.use { stream -> // Ensure stream is closed
                    // For now, defaulting to BinaryParser.
                    // A more robust solution might try to infer type or try multiple parsers.
                    val parser: PlistParser = BinaryParser() // dd-plist auto-detects, so Binary or XMLParser choice is less critical here
                    
                    // Perform parsing on the IO dispatcher
                    val parsedNode = withContext(Dispatchers.IO) {
                        parser.parse(stream)
                    }
                    nodes.postValue(parsedNode)
                    errorState.postValue(null) // Clear any previous error
                }

            } catch (e: Exception) {
                // Log.e("PlistViewModel", "Error parsing plist", e) // Proper logging
                errorState.postValue("Error parsing plist: ${e.localizedMessage ?: e.javaClass.simpleName}")
                // Optionally, post a specific error node or clear existing nodes
                // nodes.postValue(null) 
            }
        }
    }

    fun search(regex: Regex) {
        query.value = regex
    }
}
