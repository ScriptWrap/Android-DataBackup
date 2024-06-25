package com.xayah.feature.main.settings.about

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xayah.core.ui.component.paddingHorizontal
import com.xayah.core.ui.model.StringResourceToken
import com.xayah.core.ui.token.SizeTokens
import com.xayah.core.ui.util.fromStringId
import com.xayah.feature.main.settings.ContributorCard
import com.xayah.feature.main.settings.R
import com.xayah.feature.main.settings.SettingsScaffold

@ExperimentalFoundationApi
@ExperimentalLayoutApi
@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@Composable
fun PageTranslatorsSettings() {
    val context = LocalContext.current
    val viewModel = hiltViewModel<IndexViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(null) {
        viewModel.emitIntentOnIO(IndexUiIntent.Load)
    }

    SettingsScaffold(
        scrollBehavior = scrollBehavior,
        snackbarHostState = viewModel.snackbarHostState,
        title = StringResourceToken.fromStringId(R.string.translators),
        actions = {}
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .paddingHorizontal(SizeTokens.Level16),
            verticalArrangement = Arrangement.spacedBy(SizeTokens.Level8)
        ) {
            item {
                Spacer(modifier = Modifier.size(SizeTokens.Level0))
            }

            items(items = uiState.translators) {
                ContributorCard(
                    avatar = it.avatar, name = it.name, desc = it.lang
                ) {
                    viewModel.emitIntentOnIO(IndexUiIntent.ToBrowser(context, it.link))
                }
            }
        }
    }
}
