package com.example.dockerwebui.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.Font
import com.example.dockerwebui.SelectedMode
import kotlinx.coroutines.CoroutineScope

@Composable
fun rightPanelContainers(withToFill: Float, font: Font, appState: AppState, scope: CoroutineScope) {
    var userInputSearch by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(withToFill)
            .background(Color(27, 45, 68))
            .padding(32.dp)
    ) {
        if (appState.contentToDisplay == SelectedMode.CONTAINERS){
        containersTopBar(
            font = font,
            appState = appState,
            scope = scope,
            userInputSearch = userInputSearch,
            onSearchChange = { userInputSearch = it }
        )

        containersStatsCards(
            font = font,
            appState = appState
        )

        containersTable(
            font = font,
            appState = appState,
            scope = scope,
            searchQuery = userInputSearch
        )}

        shell(appState = appState , font = font, scope = scope)
    }
}
