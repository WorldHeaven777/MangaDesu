package eu.kanade.tachiyomi.data.preference

/**
 * This class stores the keys for the preferences in the application.
 */
object PreferenceKeys {

    const val theme = "pref_theme_key"

    const val rotation = "pref_rotation_type_key"

    const val enableTransitions = "pref_enable_transitions_key"

    const val doubleTapAnimationSpeed = "pref_double_tap_anim_speed"

    const val showPageNumber = "pref_show_page_number_key"

    const val trueColor = "pref_true_color_key"

    const val fullscreen = "fullscreen"

    const val keepScreenOn = "pref_keep_screen_on_key"

    const val customBrightness = "pref_custom_brightness_key"

    const val customBrightnessValue = "custom_brightness_value"

    const val colorFilter = "pref_color_filter_key"

    const val colorFilterValue = "color_filter_value"

    const val colorFilterMode = "color_filter_mode"

    const val defaultViewer = "pref_default_viewer_key"

    const val imageScaleType = "pref_image_scale_type_key"

    const val zoomStart = "pref_zoom_start_key"

    const val readerTheme = "pref_reader_theme_key"

    const val cropBorders = "crop_borders"

    const val cropBordersWebtoon = "crop_borders_webtoon"

    const val readWithTapping = "reader_tap"

    const val readWithLongTap = "reader_long_tap"

    const val readWithVolumeKeys = "reader_volume_keys"

    const val readWithVolumeKeysInverted = "reader_volume_keys_inverted"

    const val webtoonSidePadding = "webtoon_side_padding"

    const val webtoonEnableZoomOut = "webtoon_enable_zoom_out"

    const val updateOnlyNonCompleted = "pref_update_only_non_completed_key"

    const val autoUpdateTrack = "pref_auto_update_manga_sync_key"

    const val lastUsedCatalogueSource = "last_catalogue_source"

    const val lastUsedCategory = "last_used_category"

    const val catalogueAsList = "pref_display_catalogue_as_list"

    const val enabledLanguages = "source_languages"

    const val sourcesSort = "sources_sort"

    const val backupDirectory = "backup_directory"

    const val downloadsDirectory = "download_directory"

    const val downloadOnlyOverWifi = "pref_download_only_over_wifi_key"

    const val numberOfBackups = "backup_slots"

    const val backupInterval = "backup_interval"

    const val removeAfterReadSlots = "remove_after_read_slots"

    const val deleteRemovedChapters = "delete_removed_chapters"

    const val removeAfterMarkedAsRead = "pref_remove_after_marked_as_read_key"

    const val libraryUpdateInterval = "pref_library_update_interval_key"

    const val libraryUpdateRestriction = "library_update_restriction"

    const val libraryUpdateCategories = "library_update_categories"

    const val libraryUpdatePrioritization = "library_update_prioritization"

    const val filterDownloaded = "pref_filter_downloaded_key"

    const val filterUnread = "pref_filter_unread_key"

    const val filterCompleted = "pref_filter_completed_key"

    const val filterTracked = "pref_filter_tracked_key"

    const val filterMangaType = "pref_filter_manga_type_key"

    const val librarySortingMode = "library_sorting_mode"

    const val automaticUpdates = "automatic_updates"

    const val automaticExtUpdates = "automatic_ext_updates"

    const val onlySearchPinned = "only_search_pinned"

    const val downloadNew = "download_new"

    const val downloadNewCategories = "download_new_categories"

    const val libraryLayout = "pref_display_library_layout"

    const val gridSize = "grid_size"

    const val uniformGrid = "uniform_grid"

    const val lang = "app_language"

    const val dateFormat = "app_date_format"

    const val defaultCategory = "default_category"

    const val skipRead = "skip_read"

    const val skipFiltered = "skip_filtered"

    const val downloadBadge = "display_download_badge"

    const val useBiometrics = "use_biometrics"

    const val lockAfter = "lock_after"

    const val lastUnlock = "last_unlock"

    const val secureScreen = "secure_screen"

    const val removeArticles = "remove_articles"

    const val skipPreMigration = "skip_pre_migration"

    const val refreshCoversToo = "refresh_covers_too"

    const val updateOnRefresh = "update_on_refresh"

    const val showLibraryUpdateErrors = "show_library_update_errors"

    const val alwaysShowChapterTransition = "always_show_chapter_transition"

    const val enableDoh = "enable_doh"

    fun trackUsername(syncId: Int) = "pref_mangasync_username_$syncId"

    fun trackPassword(syncId: Int) = "pref_mangasync_password_$syncId"

    fun trackToken(syncId: Int) = "track_token_$syncId"
}
