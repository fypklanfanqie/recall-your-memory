package com.echo.recall.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRepositorySearchPatternTest {

    @Test
    fun `wraps the query with wildcards`() {
        assertEquals("%排期%", NoteRepository.searchPattern("排期"))
        assertEquals("%todo%", NoteRepository.searchPattern("todo"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("%排期%", NoteRepository.searchPattern("  排期  "))
    }

    @Test
    fun `escapes LIKE wildcards so user input stays literal`() {
        assertEquals("%100\\%\\_done%", NoteRepository.searchPattern("100%_done"))
        assertEquals("%a\\%b%", NoteRepository.searchPattern("a%b"))
    }

    @Test
    fun `escapes the escape character first`() {
        assertEquals("%a\\\\b%", NoteRepository.searchPattern("a\\b"))
        assertEquals("%\\\\\\%%", NoteRepository.searchPattern("\\%"))
    }

    @Test
    fun `blank query becomes a match everything pattern`() {
        assertEquals("%%", NoteRepository.searchPattern(""))
        assertEquals("%%", NoteRepository.searchPattern("   "))
    }
}
