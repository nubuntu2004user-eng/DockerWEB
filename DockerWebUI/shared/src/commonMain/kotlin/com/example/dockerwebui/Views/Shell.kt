package com.example.dockerwebui.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun shell(){
    var input by remember { mutableStateOf("") }
    ElevatedCard(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().weight(0.2f)) {
            ElevatedButton(onClick = {}){
                Text("Host")
            }
        }
        Row(modifier = Modifier.fillMaxWidth().weight(0.6f).background(Color.Black)) {
            Text("Will be shell")
        }
        Row(modifier = Modifier.fillMaxWidth().weight(0.25f)){
            TextField(value = input , onValueChange = {input = it} , trailingIcon = {
                IconButton(onClick = {}){
                    Icon(
                        imageVector = Icons.Filled.RocketLaunch,
                        contentDescription = null
                    )
                }
            },
                placeholder = {Text("Enter command to run end click button to the right to run")},
                modifier = Modifier.fillMaxSize())
        }
    }
}