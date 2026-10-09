package org.vaachak.reader.leisure.ui.testability

object Tid {
    object Screen {
        const val bookshelf = "screen_bookshelf"
        const val reader = "screen_reader"
        const val bookmarks = "screen_bookmarks"
        const val toc = "screen_toc"
        const val readerSearch = "screen_reader_search"
        const val readerSettings = "screen_reader_settings"
        const val ttsSettings = "screen_tts_settings"
        const val bookHighlights = "screen_book_highlights"
        const val settings = "screen_settings"
        const val catalog = "screen_catalog"
        const val catalogManager = "screen_catalog_manager"
        const val highlights = "screen_highlights"
        const val login = "screen_login"
        const val vault = "screen_vault"
        const val readerProfiles = "screen_reader_profiles"
        const val session = "screen_session"
        const val aiConfig = "screen_ai_config"
        const val syncSettings = "screen_sync_settings"
        const val defaultAppearance = "screen_default_appearance"
        const val appAppearance = "screen_app_appearance"
        const val dictionary = "screen_dictionary"
        const val tts = "screen_tts"
        const val librarySettings = "screen_library_settings"
        const val aiBottomSheet = "screen_ai_bottom_sheet"
    }

    object Tab {
        const val books = "tab_books"
        const val comics = "tab_comics"
        const val audio = "tab_audio"
        const val settings = "tab_settings"
    }

    object Tabs {
        const val books = Tab.books
        const val comics = Tab.comics
        const val audio = Tab.audio
        const val settings = Tab.settings
    }

    object Library {
        const val add = "library_add"
        const val search = "library_search"
        const val sync = "library_sync"
        const val catalog = "library_catalog"
        const val highlights = "library_highlights"
        const val sort = "library_sort"
        const val sortProgress = "library_sort_progress"
        const val sortRecent = "library_sort_recent"
        const val sortTitle = "library_sort_title"
        const val first = "library_book_first"
        const val progressFirst = "library_progress_first"
        const val formatFirst = "library_format_first"
        const val bookmarkFirst = "library_bookmark_first"
        const val filter = "library_filter"
        const val language = "library_language"

        const val ADD = add
        const val SEARCH = search
        const val SYNC = sync
        const val CATALOG = catalog
        const val HIGHLIGHTS = highlights
        const val SORT = sort
        const val SORT_PROGRESS = sortProgress
        const val SORT_RECENT = sortRecent
        const val SORT_TITLE = sortTitle
        const val FIRST = first
        const val PROGRESS_FIRST = progressFirst
        const val FORMAT_FIRST = formatFirst
        const val BOOKMARK_FIRST = bookmarkFirst
        const val FILTER = filter
        const val LANGUAGE = language

        fun bookByHash(bookHash: String): String = "library_book_${bookHash.asTidSegment()}"
        fun progressByHash(bookHash: String): String = "library_progress_${bookHash.asTidSegment()}"
        fun formatByHash(bookHash: String): String = "library_format_${bookHash.asTidSegment()}"
        fun bookmarkByHash(bookHash: String): String = "library_bookmark_${bookHash.asTidSegment()}"
        fun deleteByHash(bookHash: String): String =
            "library_delete_${bookHash.asTidSegment()}"
    }

    object Bookshelf {
        const val SETTINGS_TAB = Tab.settings
        const val OPEN_SETTINGS = "bookshelf_open_settings"
    }

    object Catalog {
        const val header = "catalog_header"
        const val back = "catalog_back"
        const val itemFirst = "catalog_item_first"
        const val serverFirst = "catalog_server_first"
        const val folderFirst = "catalog_folder_first"
        const val bookFirst = "catalog_book_first"
        const val bookActionFirst = "catalog_book_action_first"
        const val paginationNext = "catalog_pagination_next"
        const val paginationPrev = "catalog_pagination_prev"
        const val breadcrumbFirst = "catalog_breadcrumb_first"
        const val breadcrumbLast = "catalog_breadcrumb_last"
        const val snackbar = "catalog_snackbar"
        const val snackbarActionViewLibrary = "catalog_snackbar_action_view_library"

        const val HEADER = header
        const val BACK = back
        const val ITEM_FIRST = itemFirst
        const val SERVER_FIRST = serverFirst
        const val FOLDER_FIRST = folderFirst
        const val BOOK_FIRST = bookFirst
        const val BOOK_ACTION_FIRST = bookActionFirst
        const val PAGINATION_NEXT = paginationNext
        const val PAGINATION_PREV = paginationPrev
        const val BREADCRUMB_FIRST = breadcrumbFirst
        const val BREADCRUMB_LAST = breadcrumbLast
        const val SNACKBAR = snackbar
        const val SNACKBAR_ACTION_VIEW_LIBRARY = snackbarActionViewLibrary

        fun item(kind: String, stable: String): String =
            "catalog_item_${kind.asTidSegment()}_${stable.asTidSegment()}"

        fun bookActionByTitle(title: String): String =
            "catalog_book_action_${title.asTidSegment()}"
    }

    object CatalogManager {
        const val back = "catalog_manager_back"
        const val add = "catalog_manager_add"
        const val first = "catalog_manager_item_first"
        const val dialogTitle = "catalog_manager_dialog_title"
        const val dialogUrl = "catalog_manager_dialog_url"
        const val dialogUser = "catalog_manager_dialog_user"
        const val dialogPass = "catalog_manager_dialog_pass"
        const val dialogSave = "catalog_manager_dialog_save"
        const val dialogCancel = "catalog_manager_dialog_cancel"

        fun item(id: String): String = "catalog_manager_item_${id.asTidSegment()}"
        fun edit(id: String): String = "catalog_manager_edit_${id.asTidSegment()}"
        fun delete(id: String): String = "catalog_manager_delete_${id.asTidSegment()}"
    }

    object Vault {
        const val title = "vault_title"
        const val addProfile = "profile_add"
        const val switchProfile = "profile_switch"

        fun profile(id: String): String = "profile_$id"
    }

    object Reader {
        const val back = "reader_back"
        const val tts = "reader_tts"
        const val bookmark = "reader_bookmark"
        const val bookmarkToggle = bookmark
        const val toc = "reader_toc"
        const val search = "reader_search"
        const val bookmarksList = "reader_bookmarks_list"
        const val highlights = "reader_highlights"
        const val addHighlight = "reader_add_highlight"
        const val tocClose = "toc_close"
        const val tocItemFirst = "toc_item_first"
        const val searchClose = "reader_search_close"
        const val searchResultFirst = "reader_search_result_first"
        const val overflow = "reader_overflow"
        const val menuSearch = "reader_menu_search"
        const val menuRecap = "reader_menu_recap"
        const val settings = "reader_settings"
        const val menuSettings = settings
        const val settingsClose = "reader_settings_close"
        const val highlightCreatedBanner = "highlight_created_banner"

        const val BOOKMARK = bookmark
        const val TOC = toc
        const val TTS = tts
        const val SEARCH = search
        const val SETTINGS = settings
        const val ADD_HIGHLIGHT = addHighlight
        const val TOC_CLOSE = tocClose
        const val TOC_ITEM_FIRST = tocItemFirst
        const val SEARCH_CLOSE = searchClose
        const val SEARCH_RESULT_FIRST = searchResultFirst
        const val SETTINGS_CLOSE = settingsClose

        fun tocItemByHref(href: String): String = "toc_item_${href.asTidSegment()}"
    }

    object ReaderSettings {
        const val close = Reader.settingsClose
        const val toggleAi = "reader_settings_toggle_ai"
        const val togglePublisherStyles = "reader_settings_toggle_publisher_styles"
        const val themeLight = "reader_settings_theme_light"
        const val themeDark = "reader_settings_theme_dark"
        const val themeSepia = "reader_settings_theme_sepia"
        const val fontSizeSlider = "reader_settings_font_size_slider"
        const val lineHeightSlider = "reader_settings_line_height_slider"

        fun fontOption(fontName: String): String =
            "reader_settings_font_${fontName.asTidSegment()}"
    }

    object Tts {
        const val pill = "tts_pill"
        const val playPause = "tts_play_pause"
        const val stop = "tts_stop"
        const val next = "tts_next"
        const val prev = "tts_prev"
        const val settings = "tts_settings"
        const val settingsClose = "tts_settings_close"
    }

    object BookHighlights {
        const val close = "book_highlights_close"
        const val first = "book_highlight_first"
    }

    object Bookmarks {
        const val close = "bookmarks_close"
        const val first = "bookmark_item_first"

        const val CLOSE = close
        const val FIRST = first

        fun item(id: String): String = "bookmark_item_${id.asTidSegment()}"
    }

    object Settings {
        const val tabGlobal = "settings_tab_global"
        const val tabBook = "settings_tab_book"
        const val tabContent = "settings_tab_content"
        const val tabIntelligence = "settings_tab_intelligence"
    }

    object Login {
        const val back = "login_back"
        const val tabSignIn = "login_tab_sign_in"
        const val tabRegister = "login_tab_register"
        const val testConnection = "login_test_connection"
        const val submit = "login_submit"
        const val username = "login_username"
        const val password = "login_password"
        const val togglePassword = "login_toggle_password"
        const val useCustomServer = "login_use_custom_server"
        const val serverUrl = "login_server_url"
        const val deviceName = "login_device_name"
    }

    object Session {
        const val back = "session_back"
        const val itemFirst = "session_item_first"
        const val resumeFirst = "session_resume_first"

        fun item(stable: String): String = "session_item_${stable.asTidSegment()}"
    }

    object Highlights {
        const val back = "highlights_back"
        const val filterBar = "highlights_filter_bar"
        const val filterToggle = "highlights_filter_toggle"
        const val filterChipFirst = "highlights_filter_chip_first"
        const val first = "highlight_item_first"

        const val BACK = back
        const val FILTER_BAR = filterBar
        const val FILTER_TOGGLE = filterToggle
        const val FILTER_CHIP_FIRST = filterChipFirst
        const val FIRST = first

        fun filterChip(tagName: String): String = "highlights_filter_${tagName.asTidSegment()}"
        fun item(id: String): String = "highlight_item_${id.asTidSegment()}"
        fun delete(id: String): String = "highlight_delete_${id.asTidSegment()}"
        const val DELETE_FIRST = "highlight_delete_first"
    }

    object AiBottomSheet {
        const val actionExplain = "ai_action_explain"
        const val actionWho = "ai_action_who"
        const val actionVisualize = "ai_action_visualize"
    }
}

private fun String.asTidSegment(): String {
    val normalized = lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return normalized.ifEmpty { "item" }
}
