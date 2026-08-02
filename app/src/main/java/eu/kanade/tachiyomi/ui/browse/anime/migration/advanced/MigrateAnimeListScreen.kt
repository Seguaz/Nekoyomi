package eu.kanade.tachiyomi.ui.browse.anime.migration.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.MigrateAnimeListScreenContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

class MigrateAnimeListScreen(
    private val animeIds: List<Long>,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateAnimeListScreenModel(animeIds) }
        val state by screenModel.state.collectAsState()

        val migrationCompleted = stringResource(AYMR.strings.migration_completed)
        LaunchedEffect(state.finished) {
            if (state.finished) {
                context.toast(migrationCompleted)
                navigator.pop()
            }
        }

        MigrateAnimeListScreenContent(
            state = state,
            migrateFlags = { screenModel.migrateFlags.get() },
            navigateUp = navigator::pop,
            onClickItem = { navigator.push(AnimeScreen(it.newAnime.id)) },
            onChangeMatch = { navigator.push(MigrateAnimeSearchSelectScreen(it.oldAnime.id)) },
            onRemoveItem = { screenModel.removeAnime(it.oldAnime.id) },
            onClickMigrate = screenModel::openMigrateDialog,
            onDismissDialog = screenModel::dismissDialog,
            onConfirmMigrate = screenModel::startMigration,
        )
    }
}
