package org.sableos.start.visualreview

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.sableos.design.SableGlobalTheme

abstract class ReviewFixtureActivity(
    private val title: String,
) : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SableGlobalTheme(window = window) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Text(
                        text = "Mac visual fixture · production identity and icon only",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

class WeatherFixtureActivity : ReviewFixtureActivity("Sable Weather")

class MailFixtureActivity : ReviewFixtureActivity("Sable Mail")

class CalculatorFixtureActivity : ReviewFixtureActivity("Sable Calculator")

class ReviewWeatherSnapshotProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor =
        MatrixCursor(COLUMNS).apply {
            addRow(
                arrayOf<Any?>(
                    "Weather",
                    "Jersey City · 68° · partly cloudy",
                    "LIVE",
                    System.currentTimeMillis() - 8 * 60 * 1000L,
                ),
            )
        }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.sableos.weather.snapshot"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("Read-only fixture")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only fixture")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only fixture")

    companion object {
        private val COLUMNS =
            arrayOf("title", "detail", "availability", "observed_at")
    }
}

class ReviewMailSnapshotProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor =
        MatrixCursor(COLUMNS).apply {
            addRow(
                arrayOf<Any?>(
                    "Mail",
                    "4 mail alerts",
                    "LIVE",
                    System.currentTimeMillis(),
                ),
            )
        }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.org.sableos.mail.snapshot"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("Read-only fixture")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only fixture")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only fixture")

    companion object {
        private val COLUMNS =
            arrayOf("title", "detail", "availability", "observed_at")
    }
}
