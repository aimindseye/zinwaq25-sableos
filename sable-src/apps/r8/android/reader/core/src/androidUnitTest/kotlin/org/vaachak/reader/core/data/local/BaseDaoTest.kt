package org.vaachak.reader.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
abstract class BaseDaoTest {

    protected lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Using the actual AppDatabase class ensures schema consistency
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        onSetup()
    }

    /**
     * Optional: Override this in subclasses if you need specific
     * setup logic (like initializing a specific DAO).
     */
    open fun onSetup() {}

    @After
    fun teardown() {
        database.close()
    }
}