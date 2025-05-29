package com.example.plistviewer.ui.viewmodel

import android.app.Application // Required for AndroidViewModel
import androidx.arch.core.executor.testing.InstantTaskExecutorRule // For LiveData testing (conceptual here)
import com.example.plistviewer.data.model.NodeType
import com.example.plistviewer.data.model.PlistNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first // To get a value from StateFlow in test
import kotlinx.coroutines.runBlocking // To run suspend functions or collect flows
import kotlinx.coroutines.test.StandardTestDispatcher // For overriding Dispatchers.Main
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock // Basic mocking if available, or conceptual

@OptIn(ExperimentalCoroutinesApi::class)
class PlistViewModelTest {

    // Rule for LiveData (conceptual, won't execute in this environment but good practice)
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: PlistViewModel
    private val testDispatcher = StandardTestDispatcher()

    // Mock Application context for AndroidViewModel
    // In a real test environment, use Mockito or a similar framework.
    // Here, we'll use a simple mock if available or assume it can be passed.
    private val mockApplication: Application = mock(Application::class.java)


    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher) // Set main dispatcher for viewModelScope
        viewModel = PlistViewModel(mockApplication)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // Reset main dispatcher
    }

    private fun createSampleTree(): PlistNode {
        // Root (dict) -> child1 (string), child2 (array) -> itemA (string), itemB (dict) -> nestedKey (string)
        val nestedDict = PlistNode("nestedKey", NodeType.STRING, "Nested Value", emptyList())
        val itemB = PlistNode("itemB_key_in_dict_not_shown_if_parent_is_array", NodeType.DICTIONARY, null, listOf(nestedDict)) // Array items don't use 'key' in PlistNodeItem display path
        val itemA = PlistNode(null, NodeType.STRING, "Item A Value", emptyList())
        
        val child2 = PlistNode("child2_array", NodeType.ARRAY, null, listOf(itemA, itemB))
        val child1 = PlistNode("child1_string", NodeType.STRING, "Child One Value", emptyList())
        
        return PlistNode("_root_", NodeType.DICTIONARY, null, listOf(child1, child2))
    }
    
    // Helper to generate paths for assertion - mirrors ViewModel's internal logic
    private fun p(vararg segments: Any): List<Any> = segments.toList()

    @Test
    fun `search updates query StateFlow`() = runBlocking {
        val regex = "test".toRegex()
        viewModel.search(regex)
        assertEquals(regex, viewModel.query.value) // Direct check of StateFlow value
    }

    @Test
    fun `search expands paths for key match`() = runBlocking {
        val sampleRoot = createSampleTree()
        viewModel.nodes.value = sampleRoot // Set LiveData value directly for test
        
        val regex = "child1_string".toRegex()
        viewModel.search(regex)
        
        // Expected paths: path to root, path to child1_string
        val rootPath = p("_root_")
        val child1Path = p("_root_", "child1_string")

        // Advance dispatcher if viewModelScope.launch was used for path finding (it's synchronous now)
        testDispatcher.scheduler.advanceUntilIdle() 

        val expandedPaths = viewModel.expandedNodePaths.value
        assertTrue("Root path should be expanded", expandedPaths.contains(rootPath))
        assertTrue("child1_string path should be expanded", expandedPaths.contains(child1Path))
    }

    @Test
    fun `search expands paths for value match in nested node`() = runBlocking {
        val sampleRoot = createSampleTree()
        viewModel.nodes.value = sampleRoot
        
        val regex = "Nested Value".toRegex() // Matches nestedKey's value
        viewModel.search(regex)
        
        val rootPath = p("_root_")
        val child2Path = p("_root_", "child2_array")
        // Path to itemB (index 1 in array child2_array)
        // The key "itemB_key_in_dict_not_shown_if_parent_is_array" is on PlistNode but not used for path if parent is array.
        val itemBInArrayPath = p("_root_", "child2_array", 1) 
        val nestedKeyPath = p("_root_", "child2_array", 1, "nestedKey")

        testDispatcher.scheduler.advanceUntilIdle()
        val expandedPaths = viewModel.expandedNodePaths.value

        assertTrue("Root path should be expanded", expandedPaths.contains(rootPath))
        assertTrue("child2_array path should be expanded", expandedPaths.contains(child2Path))
        assertTrue("Path to itemB (index 1) within child2_array should be expanded", expandedPaths.contains(itemBInArrayPath))
        // The matched node itself (nestedKeyPath) should also be in expandedPaths due to current logic in findAllMatchingPaths
        assertTrue("Path to nestedKey should be expanded", expandedPaths.contains(nestedKeyPath)) 
    }

    @Test
    fun `search with no match does not expand paths unnecessarily`() = runBlocking {
        val sampleRoot = createSampleTree()
        viewModel.nodes.value = sampleRoot
        
        val initialExpandedPaths = viewModel.expandedNodePaths.value // Should be empty
        
        val regex = "nonExistentValue".toRegex()
        viewModel.search(regex)
        
        testDispatcher.scheduler.advanceUntilIdle()
        val expandedPaths = viewModel.expandedNodePaths.value
        
        // Depending on chosen behavior: could be empty or same as initial.
        // Current findAllMatchingPaths adds if match, so if no match, no additions.
        assertEquals("Paths should not change if no match", initialExpandedPaths, expandedPaths)
    }
    
    @Test
    fun `search with null regex clears query but leaves expansions`() = runBlocking {
        val sampleRoot = createSampleTree()
        viewModel.nodes.value = sampleRoot
        
        // First, perform a search that expands some paths
        viewModel.search("Nested Value".toRegex())
        testDispatcher.scheduler.advanceUntilIdle()
        val pathsAfterSearch = viewModel.expandedNodePaths.value
        assertFalse("Paths should be expanded after initial search", pathsAfterSearch.isEmpty())

        // Now, clear the search
        viewModel.search(null)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertNull("Query should be null after clearing search", viewModel.query.value)
        val pathsAfterClear = viewModel.expandedNodePaths.value
        assertEquals("Expanded paths should remain unchanged after clearing search query", pathsAfterSearch, pathsAfterClear)
    }
}
