package eu.kanade.tachiyomi.ui.browse.migration.manga

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.common.utils.mutate
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateMangaScreenModel(
    private val sourceId: Long,
    private val sourceManager: SourceManager = Injekt.get(),
    private val getFavorites: GetFavorites = Injekt.get(),
    private val getCategories : GetCategories = Injekt.get(),
) : StateScreenModel<MigrateMangaScreenModel.State>(State()) {

    private val _events: Channel<MigrationMangaEvent> = Channel()
    val events: Flow<MigrationMangaEvent> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            mutableState.update { state ->
                state.copy(source = sourceManager.getOrStub(sourceId))
            }

            getFavorites.subscribe(sourceId)
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(MigrationMangaEvent.FailedFetchingFavorites)
                    mutableState.update { state ->
                        state.copy(titleList = persistentListOf())
                    }
                }
                .map { manga ->
                    manga
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                        .toImmutableList()
                }
                .collectLatest { list ->
                    val categoryMap = list.associate { manga ->
                        manga.id to getCategories.await(manga.id).map { it.id }.toSet()
                    }
                    mutableState.update { it.copy(titleList = list, mangaCategories = categoryMap) }
                }

            getCategories.subscribe()
                .catch { logcat(LogPriority.ERROR, it) }
                .collectLatest { categories ->
                    mutableState.update { it.copy(allcategories = categories.toImmutableList()) }
                }
        }
    }

    fun toggleSelection(item: Manga) {
        mutableState.update { state ->
            val selection = state.selection.mutate { list ->
                if (!list.remove(item.id)) list.add(item.id)
            }
            state.copy(selection = selection)
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = emptySet()) }
    }

    fun toggleCategoryFilter(categoryId: Long) {
        mutableState.update { state ->
            state.copy(
                categoryFilter = state.categoryFilter.mutate { set ->
                    if (!set.remove(categoryId)) set.add(categoryId)
                }
            )
        }
    }

    fun toggleStatusFilter(status: Int) {
        mutableState.update { state ->
            state.copy(
                statusFilter = state.statusFilter.mutate { set ->
                    if (!set.remove(status)) set.add(status)
                }
            )
        }
    }

    fun selectAll() {
        mutableState.update { state ->
            state.copy(selection = state.titles.map { it.id }.toSet())
        }
    }

    fun invertSelection() {
        mutableState.update { state ->
            val newSelection = state.titles.map { it.id }.filterNot { it in state.selection }.toSet()
            state.copy(selection = newSelection)
        }
    }

    @Immutable
    data class State(
        val source: Source? = null,
        val selection: Set<Long> = emptySet(),
        val allcategories: ImmutableList<Category> = persistentListOf(),
        val categoryFilter: Set<Long> = emptySet(),
        val statusFilter : Set<Int> = emptySet(),
        private val titleList: ImmutableList<Manga>? = null,
        private val mangaCategories : Map<Long, Set<Long>> = emptyMap()
    ) {

        val titles: ImmutableList<Manga>
            get() = titleList ?: persistentListOf()

        val isLoading: Boolean
            get() = source == null || titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()

        val filteredTitles: ImmutableList<Manga>
            get() {
                var list: List<Manga> = titles
                if (categoryFilter.isNotEmpty()) {
                    list = list.filter { manga ->
                        mangaCategories[manga.id].orEmpty().any {
                            it in categoryFilter
                        }
                    }
                }
                if (statusFilter.isNotEmpty()) {
                    list = list.filter { manga ->
                        manga.status.toInt() in statusFilter
                    }
                }
                return list.toImmutableList()
            }

        val hasActiveFilters: Boolean
            get() = categoryFilter.isNotEmpty() || statusFilter.isNotEmpty()

        val selectionMode = selection.isNotEmpty()
    }
}

sealed interface MigrationMangaEvent {
    data object FailedFetchingFavorites : MigrationMangaEvent
}
