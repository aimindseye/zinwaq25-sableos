package org.sableos.media

import org.sableos.design.SableTexts

/**
 * Every line Sable Media shows, in English, by string resource name. res/values/strings.xml
 * repeats them (MediaTextTest keeps the two equal) and res/values-<lang>/strings.xml translates
 * them.
 */
object MediaText {
    val EN: Map<String, String> =
        mapOf(
            "no_episodes_yet" to "No episodes yet",
            "add_an_rss_or_atom" to
                "Add an RSS or Atom feed. Sable Media contacts only feeds you explicitly " +
                "subscribe to.",
            "no_subscriptions" to "No subscriptions",
            "paste_a_direct_podcast_feed" to
                "Paste a direct podcast feed URL below. There is no account, recommendation " +
                "profile, or ambient directory lookup.",
            "nothing_saved" to "Nothing saved",
            "save_an_episode_to_keep" to
                "Save an episode to keep it in a local listening list. Saved does not imply " +
                "downloaded.",
            "shows" to "%1\$s shows",
            "n1_recent_episode" to "1 recent episode",
            "recent_episodes" to "%1\$s recent episodes",
            "play" to "Play",
            "add_to_up_next" to "Add to up next",
            "remove_from_saved" to "Remove from saved ★",
            "save" to "Save ☆",
            "episodes_refreshed" to "%1\$s episodes · refreshed %2\$s",
            "remove_show" to "Remove show",
            "add_a_show" to "add a show",
            "paste_the_publisher_s_rss" to "Paste the publisher's RSS or Atom feed URL.",
            "feed_url" to "Feed URL",
            "subscribe" to "Subscribe",
            "working" to "Working…",
            "network_model" to "network model",
            "sable_media_refreshes_only_feeds" to
                "Sable Media refreshes only feeds you add and streams only episodes you choose. " +
                "No podcast account, analytics SDK, recommendation profile, or automatic " +
                "directory query is built into this flow.",
            "n1_min" to "< 1 min",
            "n1_min_2" to "1 min",
            "min" to "%1\$s min",
            "up_next" to "up next",
            "nothing_else_queued" to "Nothing else queued",
            "the_current_track_is_shown" to
                "The current track is shown above. Add or play more local audio to build Up Next.",
            "move_up" to "Move up",
            "move_down" to "Move down",
            "remove_from_up_next" to "Remove from up next",
            "queue_changes_are_applied_directly" to
                "Queue changes are applied directly to the active Media3 session. Sable does not " +
                "persist listening history.",
            "prev" to "Prev",
            "pause" to "Pause",
            "next" to "Next",
            "stop" to "Stop",
            "new_playlist" to "new playlist",
            "edit_playlist" to "edit playlist",
            "playlist_name" to "Playlist name",
            "no_local_tracks" to "No local tracks",
            "add_local_audio_to_the" to
                "Add local audio to the Collection before creating a playlist.",
            "cancel" to "Cancel",
            "save_playlist" to "Save playlist",
            "playlists_contain_references_to_audio" to
                "Playlists contain references to audio files you explicitly added to Sable " +
                "Media. They stay local on this device.",
            "media_settings" to "media settings",
            "start_screen" to "start screen",
            "collection" to "Collection",
            "local_library" to "local library",
            "radio" to "Radio",
            "saved_stations" to "saved stations",
            "show_mini_player" to "Show mini player",
            "show_the_active_item_below" to "Show the active item below Collection and Radio.",
            "keep_screen_awake_while_playing" to "Keep screen awake while playing",
            "prevents_display_sleep_only_while" to
                "Prevents display sleep only while Sable Media is actively playing.",
            "these_preferences_are_stored_locally" to
                "These preferences are stored locally. Sable Media does not create a cloud " +
                "profile or send playback preferences to a recommendation service.",
            "now_playing" to "now playing",
            "close" to "Close",
            "remove_from_collection" to "Remove from collection?",
            "leaves_the_sable_media_collection" to
                "\"%1\$s\" leaves the Sable Media collection, its playlists and favorites. The " +
                "file stays on the device.",
            "remove" to "Remove",
            "keep" to "Keep",
            "live" to "live · %1\$s",
            "of" to "%1\$s of %2\$s",
            "search_title_artist_or_album" to "Search title, artist or album",
            "in_enter_for_results_esc" to "%1\$s in %2\$s · Enter for results · Esc closes",
            "podcast_feed_must_use_https" to "Podcast feed must use HTTPS.",
            "podcast_feed_returned_http" to "Podcast feed returned HTTP %1\$s.",
            "podcast_feed_redirected_to_a" to "Podcast feed redirected to a non-HTTPS URL.",
            "podcast_feed_is_larger_than" to
                "Podcast feed is larger than the Sable Media safety limit.",
            "station_url_must_use_http" to "Station URL must use HTTP or HTTPS.",
            "connecting_to_the_fm_tuner" to "Connecting to the FM tuner…",
            "scanning_the_band" to "Scanning the band…",
            "seeking" to "Seeking…",
            "plug_in_wired_headphones_they" to "Plug in wired headphones: they are the antenna",
            "off" to "Off",
            "muted" to "Muted",
            "playing_on_the_speaker" to "Playing on the speaker",
            "playing_on_headphones" to "Playing on headphones",
            "media" to "Media",
            "nothing_playing" to "nothing playing",
            "playing" to " · playing",
            "paused" to " · paused",
            "nothing_queued" to "Nothing queued",
            "previous" to "Previous",
            "up_next_2" to "Up next",
            "space_on_off_j_k" to
                "Space on/off · J/K seek · Shift+J/K ±0.1 · Alt+1-9 presets · F star · O output " +
                "· M mute · R scan",
            "fm_radio" to "fm radio",
            "not_available" to "Not available",
            "this_device_has_no_fm" to "This device has no FM tuner service Sable Media can use.",
            "fm_uses_wired_headphones_as" to
                "FM uses wired headphones as its antenna. Plug them in to listen; Speaker then " +
                "plays through the speaker with the headphones still in.",
            "seek" to "◀ Seek",
            "turn_off" to "Turn off",
            "turn_on" to "Turn on",
            "seek_2" to "Seek ▶",
            "headphones" to "Headphones",
            "speaker" to "Speaker",
            "unmute" to "Unmute",
            "mute" to "Mute",
            "stop_scan" to "Stop scan",
            "scan" to "Scan",
            "unstar" to "Unstar",
            "star" to "Star",
            "no_stations_yet_press_r" to
                "No stations yet. Press R to scan, or star a station with F.",
            "tune" to "Tune",
            "remove_star" to "Remove star ★",
            "star_2" to "Star ☆",
            "local_music_or_radio" to "Local music or radio",
            "untitled_audio" to "Untitled audio",
            "local_audio" to "Local audio",
            "sable_media" to "Sable Media",
            "music_podcasts_and_radio_in" to
                "Music, podcasts, and radio in one private playback flow.",
            "search" to "search  /",
            "up_next_q" to "up next · %1\$s  Q",
            "your_local_collection_playlists_and" to
                "Your local collection, playlists, and one continuous playback queue.",
            "add_audio_files" to "Add audio files",
            "import_folder" to "Import folder",
            "loading_local_collection" to "Loading local collection",
            "indexing_saved_songs_artists_and" to
                "Indexing saved songs, artists, and albums off the UI thread.",
            "no_local_audio_yet" to "No local audio yet",
            "choose_audio_with_android_s" to
                "Choose audio with Android's document picker. Sable keeps access only to files " +
                "you explicitly select.",
            "indexing_collection" to "Indexing collection",
            "preparing_songs_artists_albums_favorites" to
                "Preparing songs, artists, albums, favorites, and A–Z navigation.",
            "no_matches_for" to "No matches for \"%1\$s\"",
            "search_looks_at_title_artist" to
                "Search looks at title, artist and album. Esc closes search.",
            "no_favorite_tracks" to "No favorite tracks",
            "mark_local_tracks_with_to" to
                "Mark local tracks with ★ to keep a quick local favorites view.",
            "create_playlist" to "Create playlist",
            "no_playlists_yet" to "No playlists yet",
            "no_matching_playlists" to "No matching playlists",
            "create_a_local_playlist_from" to
                "Create a local playlist from audio already added to Sable Media.",
            "n1_track" to "1 track",
            "tracks" to "%1\$s tracks",
            "remove_from_favorites" to "Remove from favorites ★",
            "add_to_favorites" to "Add to favorites ☆",
            "edit" to "Edit",
            "remove_playlist" to "Remove playlist",
            "space" to "Space",
            "j_prev_space_play_k" to "J prev · Space play · K next · Shift+J/K seek · Q queue",
            "internet_radio" to "internet radio",
            "manage" to "Manage",
            "no_favorite_stations" to "No favorite stations",
            "no_saved_stations" to "No saved stations",
            "mark_stations_with_to_keep" to
                "Mark stations with ★ to keep a quick local favorites view.",
            "add_a_station_by_name" to "Add a station by name and direct HTTP(S) stream URL.",
            "add_new_station" to "Add new station",
            "station_management" to "station management",
            "edit_station" to "edit station",
            "cancel_edit" to "Cancel edit",
            "save_changes" to "Save changes",
            "manage_edits_and_removes_existing" to
                "Manage edits and removes existing stations only. Use Add new station from Radio " +
                "to create one.",
            "add_station" to "add station",
            "add_one_live_radio_stream" to
                "Add one live-radio stream. Existing stations are managed separately.",
            "add_station_2" to "Add station",
            "station_name" to "Station name",
            "http_s_stream_url" to "HTTP(S) stream URL",
            "podcast_feed_document_types_are" to "Podcast feed document types are not accepted.",
            "no_playable_audio_episodes_were" to
                "No playable audio episodes were found in this feed.",
            "unknown_artist" to "Unknown artist",
            "local_collection_could_not_be" to "Local collection could not be loaded.",
            "move_focus_to_a_track" to "Move focus to a track to change its favorite.",
            "added_to_favorites" to "Added to favorites.",
            "removed_from_favorites" to "Removed from favorites.",
            "importing_1_audio_file" to "Importing 1 audio file…",
            "importing_audio_files" to "Importing %1\$s audio files…",
            "n1_local_audio_file_added" to "1 local audio file added.",
            "local_audio_files_added" to "%1\$s local audio files added.",
            "audio_import_failed_existing_collection" to
                "Audio import failed. Existing collection was not changed.",
            "scanning_audio_folder" to "Scanning audio folder…",
            "no_audio_files_found_in" to "No audio files found in that folder.",
            "n1_audio_file_imported_from" to "1 audio file imported from folder.",
            "audio_files_imported_from_folder" to "%1\$s audio files imported from folder.",
            "folder_import_failed_existing_collection" to
                "Folder import failed. Existing collection was not changed.",
            "removed_from_the_sable_media" to "Removed from the Sable Media collection.",
            "local_collection_is_still_loading" to "Local collection is still loading.",
            "playlist_removed" to "Playlist removed.",
            "this_playlist_has_no_available" to "This playlist has no available tracks.",
            "playlist_saved_locally" to "Playlist saved locally.",
            "refreshing_subscribed_feeds" to "Refreshing subscribed feeds…",
            "podcast_refresh_failed_cached_episodes" to
                "Podcast refresh failed; cached episodes were kept.",
            "podcast_removed_from_this_device" to "Podcast removed from this device.",
            "added_to_up_next" to "Added to Up Next.",
            "player_is_still_connecting_try" to "Player is still connecting. Try again.",
            "contacting_podcast_feed" to "Contacting podcast feed…",
            "podcast_subscribed_pull_down_to" to "Podcast subscribed. Pull down to refresh feeds.",
            "podcast_feed_could_not_be" to "Podcast feed could not be added.",
            "podcasts_refreshed" to "Podcasts refreshed.",
            "podcasts_refreshed_with_feed_failure" to
                "Podcasts refreshed with %1\$s feed failure(s); cached episodes were kept.",
            "live_radio" to "live radio",
            "station_removed" to "Station removed.",
            "station_saved_locally" to "Station saved locally.",
            "choose_a_station_to_edit" to "Choose a station to edit.",
            "station_updated" to "Station updated.",
        )

    val t = SableTexts(EN)
}
