package com.clkyigit.toraksb.geometry

import com.clkyigit.toraksb.data.StudyLog
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {
    @Test
    fun parsesQuotedFields() {
        assertEquals(listOf("a", "b,c", "d\"e", ""), StudyLog.parse("a,\"b,c\",\"d\"\"e\","))
    }

    @Test
    fun columnsUnique() {
        val keys = StudyLog.COLUMNS.map { it.first }
        assertEquals(keys.size, keys.toSet().size)
    }
}
