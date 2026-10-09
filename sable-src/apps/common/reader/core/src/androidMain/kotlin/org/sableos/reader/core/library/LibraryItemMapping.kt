package org.sableos.reader.core.library

import org.sableos.reader.backup.BackupItem
import org.sableos.reader.library.LegacyProgress
import org.sableos.reader.model.AuthorList
import org.sableos.reader.model.LibraryItem
import org.sableos.reader.model.PublicationKind
import org.vaachak.reader.core.domain.model.BookEntity

/** The unified-library view of a stored row; progress is the typed locator or, for old EPUBs, the legacy fields. */
fun BookEntity.toLibraryItem(collectionIds: Set<String> = emptySet()): LibraryItem = LibraryItem(
    id = bookHash,
    profileId = profileId,
    kind = PublicationKind.fromStableValue(kind),
    title = title,
    subtitle = subtitle,
    authors = AuthorList.split(author),
    series = seriesName,
    seriesIndex = seriesIndex,
    cover = coverPath,
    source = localUri,
    format = format,
    addedAt = addedDate,
    lastOpenedAt = lastRead,
    progress = LegacyProgress.of(progressJson, progress, lastCfiLocation, lastRead),
    finished = finished,
    favorite = favorite,
    collectionIds = collectionIds,
)

/** What a backup keeps of a row. No file location, cover path or anything device-specific. */
fun BookEntity.toBackupItem(): BackupItem = BackupItem(
    hash = bookHash,
    kind = kind,
    title = title,
    author = author,
    subtitle = subtitle,
    series = seriesName,
    seriesIndex = seriesIndex,
    format = format,
    language = language,
    progress = progress,
    progressJson = progressJson,
    lastLocation = lastCfiLocation,
    finished = finished,
    favorite = favorite,
    addedAt = addedDate,
    lastOpenedAt = lastRead,
)

/** A restored title with no file on this device yet: it shows as "needs file" until the person relinks it. */
fun BackupItem.toUnlinkedEntity(profileId: String): BookEntity = BookEntity(
    bookHash = hash,
    profileId = profileId,
    title = title,
    author = author,
    seriesName = series,
    language = language,
    localUri = null,
    coverPath = null,
    progress = progress,
    lastCfiLocation = lastLocation,
    addedDate = addedAt,
    lastRead = lastOpenedAt,
    updatedAt = maxOf(addedAt, lastOpenedAt),
    kind = kind,
    subtitle = subtitle,
    seriesIndex = seriesIndex,
    format = format,
    finished = finished,
    favorite = favorite,
    progressJson = progressJson,
)
