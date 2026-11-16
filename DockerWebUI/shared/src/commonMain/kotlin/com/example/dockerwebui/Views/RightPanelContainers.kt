package com.example.dockerwebui.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
        )} else if (appState.contentToDisplay == SelectedMode.IMAGES) {
            imagesTopBar(
                font = font,
                appState = appState,
                scope = scope,
                searchQuery = appState.imageSearchQuery,
                onSearchChange = { appState.imageSearchQuery = it }
            )

            imagesStatsCards(
                font = font,
                appState = appState
            )

            imagesPullSection(
                font = font,
                appState = appState,
                scope = scope
            )

            imagesTable(
                font = font,
                appState = appState,
                scope = scope,
                searchQuery = appState.imageSearchQuery
            )
        } else if (appState.contentToDisplay == SelectedMode.VOLUMES) {
            volumesTopBar(
                font = font,
                appState = appState,
                scope = scope,
                searchQuery = appState.volumeSearchQuery,
                onSearchChange = { appState.volumeSearchQuery = it }
            )

            volumesStatsCards(
                font = font,
                appState = appState
            )

            if (appState.showCreateVolumeSection) {
                volumesCreateSection(
                    font = font,
                    appState = appState,
                    scope = scope
                )
            }

            volumesTable(
                font = font,
                appState = appState,
                scope = scope,
                searchQuery = appState.volumeSearchQuery
            )
        } else if (appState.contentToDisplay == SelectedMode.NETWORKS) {
            networksTopBar(
                font = font,
                appState = appState,
                scope = scope,
                searchQuery = appState.networkSearchQuery,
                onSearchChange = { appState.networkSearchQuery = it }
            )

            networksStatsCards(
                font = font,
                appState = appState
            )

            if (appState.showCreateNetworkSection) {
                networksCreateSection(
                    font = font,
                    appState = appState,
                    scope = scope
                )
            }

            networksTable(
                font = font,
                appState = appState,
                scope = scope,
                searchQuery = appState.networkSearchQuery
            )
        }

        shell(appState = appState , font = font, scope = scope, modifier = Modifier.weight(1f).heightIn(min = 220.dp))
    }
}
