package eu.kanade.tachiyomi.ui.browse.migration.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.BaseMangaListItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import mihon.feature.migration.config.MigrationConfigScreen
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.active
import tachiyomi.presentation.core.util.selectedBackground
import tachiyomi.presentation.core.util.shouldExpandFAB

data class MigrateMangaScreen(
    private val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateMangaScreenModel(sourceId) }

        val state by screenModel.state.collectAsState()

        var showFilterDialog by remember { mutableStateOf(false) }

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        BackHandler(enabled = state.selectionMode) {
            screenModel.clearSelection()
        }

        val lazyListState = rememberLazyListState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = if (state.selectionMode) "${state.selection.size}" else state.source!!.name,
                    navigateUp = {
                        if (state.selectionMode) {
                            screenModel.clearSelection()
                        } else {
                            navigator.pop()
                        }
                    },
                    actionModeCounter = state.selection.size,
                    onCancelActionMode = screenModel::clearSelection,
                    actionModeActions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_select_all),
                                    icon = Icons.Outlined.SelectAll,
                                    onClick = screenModel::selectAll,
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_select_inverse),
                                    icon = Icons.Outlined.FlipToBack,
                                    onClick = screenModel::invertSelection,
                                ),
                            ),
                        )
                    },
                    actions = {
                        val filterTint = if (state.hasActiveFilters) {
                            MaterialTheme.colorScheme.active
                        } else {
                            LocalContentColor.current
                        }
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_select_all),
                                    icon = Icons.Outlined.SelectAll,
                                    onClick = screenModel::selectAll,
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_filter),
                                    icon = Icons.Outlined.FilterList,
                                    iconTint = filterTint,
                                    onClick = { showFilterDialog = true },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                SmallExtendedFloatingActionButton(
                    text = { Text(text = stringResource(MR.strings.migrationConfigScreen_continueButtonText)) },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        val selection = state.selection
                        screenModel.clearSelection()
                        navigator.push(MigrationConfigScreen(selection))
                    },
                    expanded = lazyListState.shouldExpandFAB(),
                    modifier = Modifier.animateFloatingActionButton(
                        visible = state.selectionMode,
                        alignment = Alignment.BottomEnd,
                    ),
                )
            },
        ) { contentPadding ->
            if (state.isEmpty) {
                EmptyScreen(
                    stringRes = MR.strings.empty_screen,
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }

            MigrateMangaContent(
                lazyListState = lazyListState,
                contentPadding = contentPadding,
                state = state,
                onClickItem = screenModel::toggleSelection,
                onClickCover = { navigator.push(MangaScreen(it.id)) },
            )
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    MigrationMangaEvent.FailedFetchingFavorites -> {
                        context.toast(MR.strings.internal_error)
                    }
                }
            }
        }

        if (showFilterDialog) {
            MigrateMangeFilterDialog(
                state = state,
                onToggleCategories = screenModel::toggleCategoryFilter,
                onToggleStatus = screenModel::toggleStatusFilter,
                onDismiss = { showFilterDialog = false },
            )
        }
    }

    @Composable
    private fun MigrateMangaContent(
        lazyListState: LazyListState,
        contentPadding: PaddingValues,
        state: MigrateMangaScreenModel.State,
        onClickItem: (Manga) -> Unit,
        onClickCover: (Manga) -> Unit,
    ) {
        FastScrollLazyColumn(
            state = lazyListState,
            contentPadding = contentPadding,
        ) {
            items(state.filteredTitles) { manga ->
                MigrateMangaItem(
                    manga = manga,
                    isSelected = manga.id in state.selection,
                    onClickItem = onClickItem,
                    onClickCover = onClickCover,
                )
            }
        }
    }

    @Composable
    private fun MigrateMangaItem(
        manga: Manga,
        isSelected: Boolean,
        onClickItem: (Manga) -> Unit,
        onClickCover: (Manga) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        BaseMangaListItem(
            modifier = modifier.selectedBackground(isSelected),
            manga = manga,
            onClickItem = { onClickItem(manga) },
            onClickCover = { onClickCover(manga) },
        )
    }

    @Composable
    private fun MigrateMangeFilterDialog(
        state: MigrateMangaScreenModel.State,
        onToggleCategories: (Long) -> Unit,
        onToggleStatus: (Int) -> Unit,
        onDismiss: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            title = { Text(text = stringResource(MR.strings.action_filter)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    if (state.statusFilter.isNotEmpty() || true) {
                        HeadingItem(MR.strings.status)
                    }
                    val statuses = listOf(
                        SManga.ONGOING to MR.strings.ongoing,
                        SManga.COMPLETED to MR.strings.completed,
                        SManga.LICENSED to MR.strings.licensed,
                        SManga.PUBLISHING_FINISHED to MR.strings.publishing_finished,
                        SManga.CANCELLED to MR.strings.cancelled,
                        SManga.ON_HIATUS to MR.strings.on_hiatus,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        statuses.forEach { (status, label) ->
                            FilterChip(
                                selected = status in state.statusFilter,
                                onClick = { onToggleStatus(status) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }

                    if (state.allcategories.isNotEmpty()) {
                        HeadingItem(MR.strings.categories)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        state.allcategories.forEach { category ->
                            FilterChip(
                                selected = category.id in state.categoryFilter,
                                onClick = { onToggleCategories(category.id) },
                                label = {
                                    Text(
                                        when (category.isSystemCategory) {
                                            true -> stringResource(MR.strings.label_default)
                                            false -> category.name
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            },
        )
    }
}
