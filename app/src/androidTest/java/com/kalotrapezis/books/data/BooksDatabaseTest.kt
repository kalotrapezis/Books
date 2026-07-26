package com.kalotrapezis.books.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class BooksDatabaseTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), BooksDatabase::class.java
    ).allowMainThreadQueries().build()
    private val dao = database.bookDao()

    @After fun close() = database.close()

    @Test fun library_orders_books_and_keeps_progress_per_uri() = runBlocking {
        dao.upsert(book("one", "content://books/one", 10))
        dao.upsert(book("two", "content://books/two", 20))
        dao.updateProgress("one", "epubcfi(/6/2)", 0.2, 30)
        dao.upsert(book("replacement", "content://books/one", 40))

        val library = dao.observeLibrary().first()
        assertEquals(listOf("one", "two"), library.map { it.id })
        assertEquals("epubcfi(/6/2)", library.first().lastCfi)
        assertEquals(0.2, library.first().progressFraction!!, 0.0)
        assertEquals("one", dao.findByUri("content://books/one")!!.id)
    }

    private fun book(id: String, uri: String, openedAt: Long) = BookEntity(
        id = id, uri = uri, title = id, author = "author", metadataIdentifier = null,
        foliateKey = "foliate:$id", sha256 = id, lastCfi = null, progressFraction = null,
        addedAt = 1, lastOpenedAt = openedAt,
    )
}
