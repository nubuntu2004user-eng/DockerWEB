package com.example.dockerwebui.Views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.Font
import com.example.dockerwebui.SelectedMode

@Composable
fun leftPanel(withToFill: Float , appState: AppState , font:Font){
    val notClicked = ButtonColors(containerColor = Color(11, 18, 27), contentColor = Color(196, 186, 154) , disabledContainerColor = Color(28, 42, 59) , disabledContentColor = Color(28, 42, 59))
    val clicked = ButtonColors(containerColor = Color(75, 112, 158), contentColor = Color(196, 186, 154) , disabledContainerColor = Color(28, 42, 59) , disabledContentColor = Color(28, 42, 59))

    val buttons = listOf<LeftPanelButton>(
        LeftPanelButton(name = "Containers" , onClick = {appState.contentToDisplay = SelectedMode.CONTAINERS} , selectedMode = SelectedMode.CONTAINERS),
        LeftPanelButton(name = "Images" , onClick = {appState.contentToDisplay = SelectedMode.IMAGES} , selectedMode = SelectedMode.IMAGES),
        LeftPanelButton(name = "Volumes" , onClick = {appState.contentToDisplay= SelectedMode.VOLUMES} , selectedMode = SelectedMode.VOLUMES),
        LeftPanelButton(name = "Networks" , onClick = {appState.contentToDisplay = SelectedMode.NETWORKS} , selectedMode = SelectedMode.NETWORKS),
//        LeftPanelButton(name = "Compose Stacks" , onClick = {appState.contentToDisplay=  SelectedMode.COMPONENTSTACKS} , selectedMode = SelectedMode.COMPONENTSTACKS),
//        LeftPanelButton(name = "AI" , onClick = {appState.contentToDisplay=  SelectedMode.TERMINAL} , selectedMode = SelectedMode.TERMINAL),
//        LeftPanelButton(name = "Settings" , onClick = {appState.contentToDisplay= SelectedMode.SETTINGS} , selectedMode = SelectedMode.SETTINGS),
        )






    Column(modifier = Modifier.fillMaxHeight().fillMaxWidth(withToFill).padding(32.dp)) {
    Text("DockerWeb" , fontSize = 32.sp , color = font.fontColor , fontFamily = font.font )
        Spacer(modifier = Modifier.fillMaxSize(0.01f))

        for (button in buttons){
        Spacer(modifier = Modifier.fillMaxSize(0.01f))

        if(button.selectedMode == appState.contentToDisplay){
        ElevatedButton(onClick = button.onClick , colors = clicked){
            Text(button.name)
        }
        }
        else{
            ElevatedButton(onClick = button.onClick , colors = notClicked){
                Text(button.name)
            }
        }
    }
    }
}
class LeftPanelButton(
    val name: String,
    val onClick: () -> Unit,
    val selectedMode: SelectedMode
)